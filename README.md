Proxy between lila and fishnet move clients

```
lila <-> redis <-> lila-fishnet <- http <- fishnet-clients
```

Start:
```
sbt
```

Start with custom port:
```
sbt -Dhttp.port=9665
```

Start with custom config file:
```
sbt -Dconfig.file=/path/to/my.conf
```

Custom config file example:
```
include "application"
redis.uri = "redis://127.0.0.1"
```
