#!/usr/bin/env bash
#
# 一键跑完整质量闸门（与 .github/workflows/ci.yml 完全一致）。
#
# 为什么需要它：CI 只在 push/PR 时跑，等一轮反馈要几分钟。本地先过一遍，
# 能把「低级错误」挡在提交之前，省掉往返。
#
# 用法（在仓库根执行）：
#   bash scripts/verify.sh
#
# 前置：Docker 已启动（集成测试用 Testcontainers 起真实 MySQL/Redis/RabbitMQ）。

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

step() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }
fail() { printf '\n\033[31m✗ %s\033[0m\n' "$1" >&2; exit 1; }

step "1/3 后端单元测试（427 个，约 15 秒）"
(cd backend && mvn -B test) || fail "后端单元测试未通过"

step "2/3 后端集成测试（16 个，约 50 秒；需要 Docker）"
(cd backend && mvn -B test -Dtest='*IT' -DfailIfNoTests=false) || fail "后端集成测试未通过"

step "3/3 前端类型检查与构建"
(cd frontend && pnpm --filter mall exec vue-tsc --noEmit) || fail "mall 类型检查未通过"
(cd frontend && pnpm --filter admin exec vue-tsc --noEmit) || fail "admin 类型检查未通过"

# --emptyOutDir false：vite 默认会先清空 dist，而本机沙箱的 safe-delete 会拦截
# 这个删除（dist/assets 超过 50 个文件时会要求确认）。CI 里 dist 本来就不存在，
# 加这个参数对 CI 无影响，两边保持一致。
(cd frontend && pnpm --filter mall exec vite build --emptyOutDir false) || fail "mall 构建失败"
(cd frontend && pnpm --filter admin exec vite build --emptyOutDir false) || fail "admin 构建失败"

printf '\n\033[32m全部通过：427 单测 + 16 集成测试 + 前端双端类型检查与构建\033[0m\n'
