import uuid
import subprocess
import sys
import os
import rtoml
from time import time


def __eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)


def execute_command(cmd, arg="", ignore_errors=False):
    cmds = cmd.split()
    if arg != "":
        cmds.append(f"{arg}")
    try:
        result = subprocess.check_output(cmds, stderr=subprocess.STDOUT)
        return str(result, "utf-8")
    except subprocess.CalledProcessError as e:
        if ignore_errors:
            return ""
        else:
            __eprint(f"{cmd} let to an error {e}. Terminating...")
            sys.exit(1)


class Transaction:

    def __init__(self):
        self.name = str(uuid.uuid4())
        # check if config is present
        if not os.path.isfile("brainstorm_config.toml"):
            standard_config = {"transactions_jar": "transactions.jar"}
            with open("brainstorm_config.toml", "w") as f:
                rtoml.dump(standard_config, f)
                __eprint(
                    "A new config was generated because no existing one was found. Please configure it."
                )
                sys.exit(1)
        with open("brainstorm_config.toml") as f:
            config = rtoml.load(f)

        self.transactions_jar = config["transactions_jar"]

    def start(self) -> str:
        start = time()
        output = execute_command(f"java -jar {self.transactions_jar} start {self.name}")
        print(f"Transaction started in {time() - start} seconds")
        return output

    def read(self, filename: str) -> str:
        start = time()
        output = execute_command(
            f"java -jar {self.transactions_jar} read {self.name} {filename}",
            ignore_errors=True,
        )
        print(f"Transaction read in {time() - start} seconds")
        return output

    def write(self, filename: str, content: str) -> str:
        start = time()
        output = execute_command(
            f"java -jar {self.transactions_jar} write {self.name} {filename}",
            arg=content,
        )
        print(f"Transaction write in {time() - start} seconds")
        return output

    def commit(self) -> str:
        start = time()
        output = execute_command(
            f"java -jar {self.transactions_jar} commit {self.name}"
        )
        print(f"Transaction commit in {time() - start} seconds")
        return output
