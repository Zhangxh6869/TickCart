# TickCart 后端镜像：多阶段构建（Maven 编译 -> JRE 运行）
# 刻意不写 `# syntax=docker/dockerfile:1`：本项目没用到 BuildKit 专属语法，
# 去掉后无需额外拉取 dockerfile 前端镜像，在只配了 registry-mirrors 的环境也能构建。
#
# 构建上下文为 backend/，由根目录 docker-compose.yml 驱动。
# 演示图片（仓库根 imgs/）与上传目录用 volume 挂载，不打进镜像：
#   /app/imgs    <- ./imgs        (tickcart.static.imgs-dir，/img/** 对外提供)
#   /app/uploads <- 命名卷         (tickcart.upload.dir，/api/files/** 对外提供)

# ---------- 阶段 1：编译 ----------
# REGISTRY 为空时用官方镜像名；直连 docker.io 超时（未配 registry-mirrors）时，
# 可在 .env 里设 REGISTRY=docker.m.daocloud.io/library/ 走加速器
ARG REGISTRY=
FROM ${REGISTRY}maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷 pom 拉依赖，源码变动时可复用这一层缓存
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# 测试在 CI / 本地 mvn test 跑，镜像构建跳过以缩短构建时间
RUN mvn -B -q clean package -DskipTests

# ---------- 阶段 2：运行 ----------
ARG REGISTRY=
FROM ${REGISTRY}eclipse-temurin:17-jre-alpine
WORKDIR /app

# tzdata 必须装：alpine 默认没有时区库，-Duser.timezone 会静默退回 UTC，
# 表现为订单时间整体偏 8 小时
RUN apk add --no-cache tzdata \
    && addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/uploads && chown -R app:app /app

ENV TZ=Asia/Shanghai
COPY --from=build /build/target/*.jar /app/app.jar

USER app
EXPOSE 8080

# MaxRAMPercentage 让 JVM 跟随容器内存上限
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Duser.timezone=Asia/Shanghai", "-jar", "/app/app.jar"]
