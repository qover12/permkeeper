#!/system/bin/sh
# Runs at post-fs-data (before system_server): prepare the shared state dir.
MODDIR=${0%/*}
D=/data/system/permkeeper
C=$D/config.json

mkdir -p "$D"
chown system:system "$D"
chmod 700 "$D"
chcon u:object_r:system_data_file:s0 "$D" 2>/dev/null

if [ ! -f "$C" ]; then
  cat > "$C" <<'EOF'
{
  "version": 1,
  "all": false,
  "apps": [],
  "categories": {
    "permissions": true,
    "notifications": true,
    "appops": true,
    "autostart": true
  }
}
EOF
fi
chown system:system "$C"
chmod 600 "$C"
chcon u:object_r:system_data_file:s0 "$C" 2>/dev/null
