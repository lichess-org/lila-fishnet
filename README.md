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

Use environment variables to start with custom config (`redis.host` and `kamon` for example):
```sh
REDIS_HOST=redis KAMON_ENABLED=true CONFIG_FORCE_kamon_influxdb_port=8888 sbt app/run
```

Or creating an `.env` file with environment variables, for example:
```sh
cp .env.example .env
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

Run code format and auto code refactor with scalafmt & scalafix:
```sh
sbt prepare
```

### release

```bash
sbt release with-defaults
```
