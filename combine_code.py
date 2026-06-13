import os

def combine_code_files(project_dir, output_file_path):
    # Extensions you want to include in the combined file
    valid_extensions = ('.java', '.xml', '.properties', '.py', '.md', '.json')

    # Directories you want to ignore to avoid cluttering your text file
    ignored_dirs = {'target', '.git', '.idea', '.settings', '.metadata', 'bin'}

    print(f"Scanning directory: {os.path.abspath(project_dir)}")

    with open(output_file_path, 'w', encoding='utf-8') as outfile:
        file_count = 0

        for root, dirs, files in os.walk(project_dir):
            # Modifying dirs in-place allows os.walk to skip ignored directories entirely
            dirs[:] = [d for d in dirs if d not in ignored_dirs]

            for file in files:
                if file.endswith(valid_extensions):
                    file_path = os.path.join(root, file)
                    relative_path = os.path.relpath(file_path, project_dir)

                    print(f"Adding: {relative_path}")

                    # Write a prominent header showing the file path
                    outfile.write("=" * 80 + "\n")
                    outfile.write(f"FILE: {relative_path}\n")
                    outfile.write("=" * 80 + "\n\n")

                    # Read and append the file contents
                    try:
                        with open(file_path, 'r', encoding='utf-8', errors='replace') as infile:
                            outfile.write(infile.read())
                    except Exception as e:
                        outfile.write(f"[ERROR READING FILE: {str(e)}]\n")

                    outfile.write("\n\n")  # Spacing between files
                    file_count += 1

    print("-" * 50)
    print(f"Success! Combined {file_count} files into: {os.path.abspath(output_file_path)}")

if __name__ == "__main__":
    # '.' means it will look in the current folder where you run the script
    # Change this path string if your project is located elsewhere
    target_project_directory = '.'

    # The output filename
    combined_output_name = 'combined_project_code.txt'

    combine_code_files(target_project_directory, combined_output_name)