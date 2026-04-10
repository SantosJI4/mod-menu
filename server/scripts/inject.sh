#!/bin/bash
# Script de injecao educacional
# Uso: sh inject.sh <pacote_do_jogo>

PACKAGE=$1

if [ -z "$PACKAGE" ]; then
    echo "[!] Uso: sh inject.sh <pacote_do_jogo>"
    exit 1
fi

echo "[*] Alvo: $PACKAGE"
echo "[*] PID do jogo: $(pidof $PACKAGE)"
echo "[+] Script executado com sucesso"
echo "[+] Timestamp: $(date)"
