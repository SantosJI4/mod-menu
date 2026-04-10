from flask import Flask, request, jsonify, send_file
import json
import os

app = Flask(__name__)

KEYS_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "keys.json")
SCRIPTS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "scripts")


def load_keys():
    if not os.path.exists(KEYS_FILE):
        return {}
    with open(KEYS_FILE, "r") as f:
        return json.load(f)


@app.route("/api/validate", methods=["POST"])
def validate_key():
    data = request.get_json(force=True, silent=True)
    if not data or "key" not in data:
        return jsonify({"message": "Key nao fornecida"}), 400

    key = str(data["key"]).strip()
    keys = load_keys()

    if key in keys and keys[key].get("active", False):
        return jsonify({"message": "OK", "valid": True}), 200

    return jsonify({"message": "Key invalida ou expirada"}), 403


@app.route("/api/download/script", methods=["GET"])
def download_script():
    auth_key = request.headers.get("X-Auth-Key", "").strip()
    keys = load_keys()

    if not auth_key or auth_key not in keys or not keys[auth_key].get("active", False):
        return jsonify({"message": "Nao autorizado"}), 401

    script_path = os.path.join(SCRIPTS_DIR, "ptr_inject.sh")
    if not os.path.exists(script_path):
        return jsonify({"message": "Script nao encontrado no servidor"}), 404

    # Ensure the path doesn't escape the scripts directory
    real_path = os.path.realpath(script_path)
    if not real_path.startswith(os.path.realpath(SCRIPTS_DIR)):
        return jsonify({"message": "Acesso negado"}), 403

    return send_file(real_path, as_attachment=True, download_name="ptr_inject.sh")


if __name__ == "__main__":
    os.makedirs(SCRIPTS_DIR, exist_ok=True)

    # Create default keys.json if it doesn't exist
    if not os.path.exists(KEYS_FILE):
        default_keys = {
            "TESTE-1234-ABCD": {"active": True, "user": "teste"},
            "KEY-5678-EFGH": {"active": True, "user": "dev"}
        }
        with open(KEYS_FILE, "w") as f:
            json.dump(default_keys, f, indent=4)
        print("[+] keys.json criado com chaves de teste")

    # Create sample ptr_inject.sh if it doesn't exist
    sample_script = os.path.join(SCRIPTS_DIR, "ptr_inject.sh")
    if not os.path.exists(sample_script):
        with open(sample_script, "w") as f:
            f.write('#!/bin/bash\n')
            f.write('# Script de injecao educacional\n')
            f.write('# Uso: sh inject.sh <pacote_do_jogo>\n\n')
            f.write('PACKAGE=$1\n\n')
            f.write('if [ -z "$PACKAGE" ]; then\n')
            f.write('    echo "[!] Uso: sh inject.sh <pacote_do_jogo>"\n')
            f.write('    exit 1\n')
            f.write('fi\n\n')
            f.write('echo "[*] Alvo: $PACKAGE"\n')
            f.write('echo "[*] PID do jogo: $(pidof $PACKAGE)"\n')
            f.write('echo "[+] Script executado com sucesso"\n')
            f.write('echo "[+] Timestamp: $(date)"\n')
        print("[+] ptr_inject.sh de exemplo criado")

    print("=" * 50)
    print("  SH Injector - Servidor")
    print("  Rodando em http://0.0.0.0:80")
    print("=" * 50)
    print("  Keys de teste:")
    print("    - TESTE-1234-ABCD")
    print("    - KEY-5678-EFGH")
    print("=" * 50)

    app.run(host="0.0.0.0", port=80)
