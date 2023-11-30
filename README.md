# Lila Fishnet

Proxy between lila and fishnet move clients

```
lila <-> redis <-> lila-fishnet <- http <- fishnet-clients
```

## Developement

Start:
```sh
sbt
```

Start with default config:
```sh
sbt app/run
```

Start with custom config (`redis.host` for example):
```
sbt -Dredis.host=redis
```

For other `config` check [Config.scala](https://github.com/lichess-org/lila-fishnet/blob/master/app/src/main/scala/Config.scala)

Run all tests (required Docker for IntegrationTest):
```
sbt app/test
```

Run a single test:
```
sbt app/testOnly lila.fishnet.ExecutorTest
```

Format:
```
sbt scalafmtAll
```
