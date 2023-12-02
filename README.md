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

Use environment variables to start with custom config (`redis.host` for example):
```sh
REDIS_HOST=redis sbt app/run
```

For other `config` check [AppConfig.scala](https://github.com/lichess-org/lila-fishnet/blob/master/app/src/main/scala/AppConfig.scala)

Run all tests (required Docker for IntegrationTest):
```sh
sbt app/test
```

Run a single test:
```sh
sbt app/testOnly lila.fishnet.ExecutorTest
```

Format:
```sh
sbt scalafmtAll
```
