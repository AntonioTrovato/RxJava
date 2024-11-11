#!/bin/bash

# Configure git
git config --global user.email "atrovato@unisa.it"
git config --global user.name "AntonioTrovato"

#TODO: CREA DELLE VARIABILI ALL'INIZIO DI QUESTO .SH IMPOSTABILI PER GENERALIZZARE L'USO DI QUESTO WORKFLOW

#TODO: JU2JMH SVUOTA LE CLASSI DI BENCHMARK NON
#TODO: INTERESSATE DAL COMMIT (SE UNA CLASSE
#TODO: DI BENCHMARK C'ERA LA SVUOTA, SE NON C'ERA
#TODO: LA CREA (OSSIA CI DEVE ESSERE LA CLASSE DI
#TODO: TEST DI UNITA') SENZA BENCHMARK

#TODO: LE CLASSI INTERNE SONO NELLA COVERAGE MATRIX COME CLASSE$CLASSE_INTERNA

#TODO: RICORDA DI AFFINARE IL CONTROLLO DELLA DIFFERENZA NEL BODY

#TODO: RICORDA DI AFFINARE LE ESCLUDE DELLO JACOCO AGENT

# File paths
MODIFIED_METHODS_FILE="modified_methods.txt"
COVERAGE_MATRIX_FILE="coverage-matrix.json"
OUTPUT_FILE="src/jmh/benchmark_classes_to_generate.txt"

# Read the hashes of the last two commits using git log
current_commit=$(git log --format="%H" -n 1)
previous_commit=$(git log --format="%H" -n 2 | tail -n 1)

# Make diff between the two commits
git_diff=$(git diff -U0 --minimal $previous_commit $current_commit)

echo "GIT DIFF:"

echo "$git_diff"

echo "=================================================================================="

# Initialize empty arrays to store deleted and added methods
modified_classes=()

# Read the diff string line by line
while IFS= read -r line; do
  # Check if the line starts with "diff --git"
  if [[ $line =~ ^\+++\ .*\/main\/java\/(.*\/)?([^\/]+)\.java$ ]]; then
    packages="${BASH_REMATCH[1]}"
    file_name="${BASH_REMATCH[2]}"

    if [[ -n "$packages" ]]; then
      packages="${packages%/}"
      packages="${packages}."  # add . if packages is not empty to obtain a correct path
    fi

    # Replace slashes with dots and remove .java extension
    class_name="${packages//\//.}${file_name%.java}"

    modified_classes+=("$class_name")
  fi
done <<< "$git_diff"

# Print each element of the list
for modified_class in "${modified_classes[@]}"; do
  echo "Modified class:"
  echo "$modified_class"
done

# write in a temporary file the fully qualified names of the modified classes
temp_file="modified_classes.txt"
printf "%s\n" "${modified_classes[@]}" > "$temp_file"

# run the java script
java -jar build/libs/rxjava-3.0.0-SNAPSHOT-all.jar "$temp_file"

# delete the file
rm "$temp_file"

# read and print the contents of modified_methods.txt
while IFS= read -r line; do
    echo "$line"
done < "modified_methods.txt"

# Initialize an empty list for test methods
declare -a test_methods

# Read modified methods from the file into an array
mapfile -t modified_methods < "$MODIFIED_METHODS_FILE"

# Read the coverage-matrix.json and extract test methods
# Assuming jq is installed for JSON parsing
for method in "${modified_methods[@]}"; do
    echo "Processing method: $method"
    # Use jq to find test cases that cover the current method
    test_cases=$(jq -r --arg method "$method" '
        to_entries | map(select(.value | index($method))) | .[].key
    ' "$COVERAGE_MATRIX_FILE")

    # Append found test cases to the test_methods array
    while IFS= read -r test_case; do
        test_methods+=("$test_case")
    done <<< "$test_cases"
done

# Print the list of test methods
echo "Test methods covering modified methods:"
printf '%s\n' "${test_methods[@]}"

# Extract fully qualified class names from test methods
declare -a class_names
for test_method in "${test_methods[@]}"; do
    # Extract the class name by removing the last part after the last dot
    class_name="${test_method%.*}"
    # Check if the class name is already in the class_names array
    already_present=false
    for existing_class in "${class_names[@]}"; do
        if [[ "$existing_class" == "$class_name" ]]; then
            already_present=true
            break
        fi
    done

    # Add class name if not already present
    if ! $already_present; then
        class_names+=("$class_name")
    fi
done

# Print the list of fully qualified class names
echo "Fully qualified class names:"
printf '%s\n' "${class_names[@]}"

# Write class names to the output file, create if it doesn't exist
#mkdir -p "src/jmh"  # Create directory if it doesn't exist
#mkdir -p "src/jmh/java"

{
    for class_name in "${class_names[@]}"; do
        echo "$class_name"  # Write each class name on a new line
    done
} > "$OUTPUT_FILE"

echo "Class names written to $OUTPUT_FILE"

# Make and build the benchmark classes
java -jar ju-to-jmh/converter-all.jar src/test/java/ build/classes/java/test/ src/jmh/java/ --class-names-file=src/jmh/benchmark_classes_to_generate.txt
gradle jmhJar

# List available benchmarks
java -jar build/libs/rxjava-3.0.0-SNAPSHOT-jmh.jar -l

# Initialize an empty list for benchmark methods to run
declare -a benchmarks_to_run

for method in "${test_methods[@]}"; do
    # Extract the method name (last part after the last dot)
    method_name="${method##*.}"

    # Replace the method name with "_Benchmark._benchmark_" + method name
    benchmark="${method%.*}._Benchmark.benchmark_${method_name}"

    # Add the new benchmark name to the list
    benchmarks_to_run+=("$benchmark")
done

# Loop through benchmarks_to_run and run the java command for each
for benchmark in "${benchmarks_to_run[@]}"; do
    echo "Running benchmark: $benchmark"
    java -jar build/libs/rxjava-3.0.0-SNAPSHOT-jmh.jar "$benchmark"
done

# Add to git all the benchmark classes generated/regenerated
for class_name in "${class_names[@]}"; do
  # Convert the class name to a file path
  file_path="src/jmh/java/$(echo "$class_name" | tr '.' '/')".java

  echo "Benchmark Class to Push in the Main Branch:"
  echo "$file_path"

  # Add the file to git
  git add "$file_path"
done

git add "src/jmh/java/se/chalmers/ju2jmh/api/JU2JmhBenchmark.java"

# Commit the changes
git commit -m "Adding the Created Benchmark Classes to the Repository"

# Push the changes to the main branch using the token
git remote set-url origin https://AntonioTrovato:${ACTIONS_TOKEN}@github.com/AntonioTrovato/RxJava.git
git push origin 3.x

echo "DONE!"
