#!/system/bin/sh
# WebUI backend helper. Runs as root (invoked by the manager's WebUI exec).
# Usage: permkeeper.sh <cmd> [args]
DIR=/data/system/permkeeper
CFG=$DIR/config.json
EXP=/sdcard/Download/permkeeper

save_cfg() {
  # stdin -> config.json with correct owner/context
  cat > "$CFG"
  chown system:system "$CFG"
  chmod 600 "$CFG"
  chcon u:object_r:system_data_file:s0 "$CFG" 2>/dev/null
}

case "$1" in
  get)
    cat "$CFG"
    ;;
  put)
    save_cfg
    echo ok
    ;;
  apps)
    # third-party packages, one per line
    pm list packages -3 | sed 's/^package://'
    ;;
  labels)
    cat "$DIR/labels.json" 2>/dev/null
    ;;
  export)
    mkdir -p "$EXP"
    ts=$(date +%Y%m%d-%H%M%S)
    cp "$CFG" "$EXP/config-$ts.json"
    echo "$EXP/config-$ts.json"
    ;;
  list_exports)
    ls "$EXP" 2>/dev/null
    ;;
  import)
    [ -f "$2" ] && save_cfg < "$2" && echo ok || echo "not found: $2"
    ;;
  *)
    echo "usage: permkeeper.sh get|put|apps|export|list_exports|import"
    ;;
esac
