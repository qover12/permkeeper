#!/system/bin/sh
# WebUI backend helper. Runs as root (invoked by the manager's WebUI exec).
# Usage: permkeeper.sh <cmd> [args]
DIR=/data/system/permkeeper
CFG=$DIR/config.json
EXP=/sdcard/Download/permkeeper

save_cfg() {
  # stdin -> config.json with correct owner/context
  # 必须原子写：直接 `cat > $CFG` 会先截断，中间存在文件为空的窗口。
  # 若模块恰在此刻读取（例如清数据时 Config.load），解析失败会回退成
  # all=false —— 即「所有应用都不受保护」，且只在日志里留一行。
  # 实测：40 次快速写入期间采样 6 份，5 份为 0 字节。
  tmp="$CFG.tmp.$$"
  if ! cat > "$tmp"; then
    rm -f "$tmp"
    return 1
  fi
  chown system:system "$tmp"
  chmod 600 "$tmp"
  chcon u:object_r:system_data_file:s0 "$tmp" 2>/dev/null
  mv -f "$tmp" "$CFG"
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
