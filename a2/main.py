import json
from transaction import Transaction


def main():

    # start a transaction
    transaction = Transaction()
    print(transaction.start())

    # construct entry
    name = input("Name of your idea: ")

    # check if entry already exists; if yes: load previous values as default

    default_idea = ""
    default_comments = ""

    read_output = transaction.read(name)
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
    print(transaction.write(name, json_str))
    print(transaction.commit())


if __name__ == "__main__":
    main()
