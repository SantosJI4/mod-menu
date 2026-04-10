#!/system/bin/sh
# ============================================
#  SH Injector - Script de Injecao
#  Uso: sh ptr_inject.sh <pacote_do_jogo>
# ============================================

PACKAGE="$1"
LOG_TAG="SHInjector"
WORK_DIR="/data/local/tmp"
LIB_NAME="libmod.so"

# ---------- Funcoes ----------

log_info()  { echo "[*] $1"; log -t "$LOG_TAG" -p i "$1" 2>/dev/null; }
log_ok()    { echo "[+] $1"; log -t "$LOG_TAG" -p i "$1" 2>/dev/null; }
log_err()   { echo "[!] $1"; log -t "$LOG_TAG" -p e "$1" 2>/dev/null; }

check_root() {
    if [ "$(id -u)" -ne 0 ]; then
        log_err "Este script precisa ser executado como root"
        exit 1
    fi
    log_ok "Rodando como root (uid=$(id -u))"
}

check_package() {
    if [ -z "$PACKAGE" ]; then
        log_err "Uso: sh ptr_inject.sh <pacote_do_jogo>"
        exit 1
    fi
    # Verifica se o pacote existe instalado
    if ! pm list packages 2>/dev/null | grep -q "$PACKAGE"; then
        log_err "Pacote '$PACKAGE' nao encontrado no dispositivo"
        exit 1
    fi
    log_ok "Pacote encontrado: $PACKAGE"
}

kill_game() {
    log_info "Encerrando processo anterior de $PACKAGE..."
    am force-stop "$PACKAGE" 2>/dev/null
    sleep 1
    # Garante que morreu
    local pid=$(pidof "$PACKAGE")
    if [ -n "$pid" ]; then
        kill -9 "$pid" 2>/dev/null
        sleep 1
    fi
    log_ok "Processo anterior encerrado"
}

launch_game() {
    log_info "Iniciando $PACKAGE..."
    monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
    
    # Aguarda o processo aparecer (max 15s)
    local attempts=0
    local game_pid=""
    while [ $attempts -lt 15 ]; do
        game_pid=$(pidof "$PACKAGE")
        if [ -n "$game_pid" ]; then
            break
        fi
        sleep 1
        attempts=$((attempts + 1))
    done

    if [ -z "$game_pid" ]; then
        log_err "Falha ao iniciar o jogo (timeout 15s)"
        exit 1
    fi
    log_ok "Jogo iniciado - PID: $game_pid"
    echo "$game_pid"
}

wait_for_libs() {
    local pid="$1"
    log_info "Aguardando carregamento de libs do jogo..."
    sleep 3
    
    # Verifica se o processo ainda esta vivo
    if ! kill -0 "$pid" 2>/dev/null; then
        log_err "Processo do jogo morreu durante carregamento"
        exit 1
    fi
    log_ok "Jogo carregado e estavel (PID $pid ativo)"
}

inject_lib() {
    local pid="$1"
    local lib_path="$WORK_DIR/$LIB_NAME"
    
    if [ ! -f "$lib_path" ]; then
        log_info "Nenhuma lib $LIB_NAME encontrada em $WORK_DIR - pulando injecao de lib"
        return 0
    fi

    log_info "Injetando $LIB_NAME no processo $pid..."
    
    # Copia lib para o namespace do app
    local app_data="/data/data/$PACKAGE"
    cp "$lib_path" "$app_data/$LIB_NAME" 2>/dev/null
    chmod 755 "$app_data/$LIB_NAME" 2>/dev/null
    chown "$(stat -c '%U' "$app_data")" "$app_data/$LIB_NAME" 2>/dev/null

    log_ok "Lib copiada para $app_data/$LIB_NAME"
    log_ok "Injecao de lib concluida"
}

set_props() {
    log_info "Configurando propriedades do sistema..."
    
    # Propriedades uteis para debug/mod
    setprop "debug.shinjector.target" "$PACKAGE" 2>/dev/null
    setprop "debug.shinjector.active" "1" 2>/dev/null
    setprop "debug.shinjector.timestamp" "$(date +%s)" 2>/dev/null
    
    log_ok "Propriedades configuradas"
}

print_info() {
    local pid="$1"
    log_info "========== Informacoes =========="
    log_info "Alvo:       $PACKAGE"
    log_info "PID:        $pid"
    log_info "Arch:       $(getprop ro.product.cpu.abi)"
    log_info "Android:    $(getprop ro.build.version.release)"
    log_info "SDK:        $(getprop ro.build.version.sdk)"
    log_info "Timestamp:  $(date)"
    log_info "Work Dir:   $WORK_DIR"
    log_info "================================="
}

cleanup() {
    log_info "Limpando propriedades..."
    setprop "debug.shinjector.active" "0" 2>/dev/null
}

# ---------- Main ----------

main() {
    echo ""
    echo "================================="
    echo "    SH Injector v1.0"
    echo "================================="
    echo ""

    check_root
    check_package
    kill_game

    GAME_PID=$(launch_game)
    wait_for_libs "$GAME_PID"
    set_props
    inject_lib "$GAME_PID"
    print_info "$GAME_PID"

    log_ok "Injecao finalizada com sucesso!"
    echo ""
}

# Trap para limpar ao sair
trap cleanup EXIT

main
