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
                    # 🟢 FIXED: Removed the accidental ':' from the end of this line
                    file_path = os.path.join(root, file)
                    relative_path = os.path.relpath(file_path, project_dir)

                    print(f"Adding: {relative_path}")

                    # Write a prominent header showing the file path
                    outfile.write("=" * 80 + "\n")
                    outfile.write(f"FILE: {relative_path}\n")
                    outfile.write("=" * 80 + "\n\n")

                    try:
                        with open(file_path, 'r', encoding='utf-8') as infile:
                            outfile.write(infile.read())
                    except Exception as e:
                        outfile.write(f"[ERROR READING FILE: {str(e)}]")

                    outfile.write("\n\n")
                    file_count += 1

    print(f"\nSuccessfully combined {file_count} files into: {output_file_path}")

if __name__ == "__main__":
    # Aggregates files within the current execution folder context
    current_directory = os.path.dirname(os.path.abspath(__file__)) if __file__ else os.getcwd()
    output_snapshot = os.path.join(current_directory, "combined_project_code.txt")

    combine_code_files(current_directory, output_snapshot)