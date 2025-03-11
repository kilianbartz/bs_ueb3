find . -maxdepth 1 -type f -regextype posix-extended -regex './[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.t' -exec rm {} \;
zfs list -H -o name -t snapshot | xargs -n1 zfs destroy
