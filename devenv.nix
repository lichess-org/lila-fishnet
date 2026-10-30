{
  pkgs,
  lib,
  config,
  ...
}:
{
  languages = {
    scala = {
      enable = true;
      sbt.enable = true;
    };
  };

  processes.lila-fishnet = {
    exec = ''
      if [ -f "$DEVENV_ROOT/app/src/main/scala/http/FishnetRoutes.scala" ]; then
        cd "$DEVENV_ROOT"
      else
        dir="''${LILA_FISHNET_PATH:-$DEVENV_ROOT/../lila-fishnet}"
        cd "$dir" || { echo "lila-fishnet checkout not found at $dir; clone it there or set LILA_FISHNET_PATH" >&2; exit 1; }
      fi
      exec ${config.languages.scala.sbt.package}/bin/sbt app/run
    '';
  };
}
