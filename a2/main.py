import subprocess
import uuid
import os
import rtoml
import sys
import json

def __execute_command(cmd, arg=""):
    cmds = cmd.split()
    if arg != "":
        cmds.append(f'"{arg}"')
    result = subprocess.run(cmds)
    if(result.returncode != 0):
        __eprint(f"{cmd} let to an error. Terminating...")
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
    idea = input("Description of your idea: ")
    line = "a"
    comments = []
    while line != "":
        comments.append(line)
        line = input("Next comment (leave blank to stop): ")
    comments = comments[1:]
    entry = {"name": name, "idea": idea, "comments": comments}

    # commit entry
    json_str = json.dumps(entry)
    escaped_json = json_str.replace('"', '\\"')
    __execute_command(f"java -jar {TRANSACTIONS_JAR} write {my_uuid} {name}", arg=escaped_json)
    __execute_command(f"java -jar {TRANSACTIONS_JAR} commit {my_uuid}")


if __name__ == "__main__":
    main()

