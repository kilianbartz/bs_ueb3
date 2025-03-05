import subprocess
import uuid
import os
import rtoml
import sys
import json

def __execute_command(cmd, arg="", ignore_errors=False):
    cmds = cmd.split()
    if arg != "":
        cmds.append(f'{arg}')
    try:
        result = subprocess.check_output(cmds, stderr=subprocess.STDOUT)
        return str(result, "utf-8")
    except subprocess.CalledProcessError as e:
        if ignore_errors:
            return ""
        else:
            __eprint(f"{cmd} let to an error {e}. Terminating...")
            sys.exit(1)
    
        

def __eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)

def main():
    # check if config is present
    if not os.path.isfile("brainstorm_config.toml"):
        standard_config = {"transactions_jar": "transactions.jar"}
        with(open("brainstorm_config.toml", "w") as f):
            rtoml.dump(standard_config, f)
            __eprint("A new config was generated because no existing one was found. Please configure it.")
            sys.exit(1)
    with(open("brainstorm_config.toml") as f):
        config = rtoml.load(f)
    
    TRANSACTIONS_JAR = config["transactions_jar"]

    # start a transaction
    my_uuid = str(uuid.uuid4())
    __execute_command(f"java -jar {TRANSACTIONS_JAR} start {my_uuid}")
    
    # construct entry
    name = input("Name of your idea: ")

    # check if entry already exists; if yes: load previous values as default

    default_idea = ""
    default_comments = ""

    read_output = __execute_command(f"java -jar {TRANSACTIONS_JAR} read {my_uuid} {name}", ignore_errors=True)
    if read_output != "":
        old_values = json.loads(read_output)
        default_idea = f" ({old_values['idea']})"
        default_comments = f" ({old_values['comments']})"

    idea = input(f"Description of your idea{default_idea}: ")
    line = "a"
    comments = []
    while line != "":
        comments.append(line)
        line = input(f"Next comment{default_comments} [leave blank to stop]: ")
    comments = comments[1:]
    entry = {"name": name, "idea": idea, "comments": comments}

    # commit entry
    json_str = json.dumps(entry)
    write_output = __execute_command(f"java -jar {TRANSACTIONS_JAR} write {my_uuid} {name}", arg=json_str)
    print(write_output)
    commit_output = __execute_command(f"java -jar {TRANSACTIONS_JAR} commit {my_uuid}")
    print(commit_output)


if __name__ == "__main__":
    main()

