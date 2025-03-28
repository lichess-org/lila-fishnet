#!/bin/sh

REMOTE=$1
REMOTE_DIR="/home/lila-fishnet"

echo "Deploy to server $REMOTE:$REMOTE_DIR"

sbt stage

if [ $? != 0 ]; then
  echo "Deploy canceled"
  exit 1
fi

RSYNC_OPTIONS=" \
  --archive \
  --no-o --no-g \
  --force \
  --delete \
  --progress \
  --compress \
  --checksum \
  --verbose \
  --exclude RUNNING_PID \
  --exclude '.git/'"

stage="app/target/universal/stage"
include="$stage/bin $stage/lib"
rsync_command="rsync $RSYNC_OPTIONS $include $REMOTE:$REMOTE_DIR"
echo "$rsync_command"
$rsync_command
echo "rsync complete"

read -n 1 -p "Press [Enter] to continue."

echo "Restart lila-fishnet"
ssh $REMOTE "chown -R lila-fishnet:lila-fishnet /home/lila-fishnet && systemctl restart lila-fishnet"

echo "Deploy complete"

