from flask import Flask, request, jsonify, send_file
import json
import os
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("SHInjector")

app = Flask(__name__)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
KEYS_FILE = os.path.join(BASE_DIR, "keys.json")
SCRIPTS_DIR = os.path.join(BASE_DIR, "scripts")

# Garante que a pasta scripts existe (roda sempre, nao depende de __main__)
os.makedirs(SCRIPTS_DIR, exist_ok=True)

# Cria keys.json padrao se nao existir
if not os.path.exists(KEYS_FILE):
    default_keys = {
        "TESTE-1234-ABCD": {"active": True, "user": "teste"},
        "KEY-5678-EFGH": {"active": True, "user": "dev"}
    }
    with open(KEYS_FILE, "w") as f:
        json.dump(default_keys, f, indent=4)
    logger.info("keys.json criado com chaves padrao")

logger.info("BASE_DIR: %s", BASE_DIR)
logger.info("SCRIPTS_DIR: %s", SCRIPTS_DIR)
logger.info("Arquivos em scripts/: %s", os.listdir(SCRIPTS_DIR) if os.path.exists(SCRIPTS_DIR) else "PASTA NAO EXISTE")


def load_keys():
    if not os.path.exists(KEYS_FILE):
        return {}
    with open(KEYS_FILE, "r") as f:
        return json.load(f)


@app.route("/", methods=["GET"])
def index():
    return jsonify({"status": "online", "app": "SH Injector API"}), 200


@app.route("/api/status", methods=["GET"])
def status():
    try:
        files = os.listdir(SCRIPTS_DIR)
    except Exception as e:
        files = [str(e)]
    return jsonify({
        "status": "online",
        "scripts_dir": SCRIPTS_DIR,
        "scripts_files": files,
        "keys_exists": os.path.exists(KEYS_FILE)
    }), 200


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

    # Find first .sh file in scripts directory
    try:
        sh_files = sorted([f for f in os.listdir(SCRIPTS_DIR) if f.endswith(".sh")])
        logger.info("Arquivos .sh encontrados: %s", sh_files)
    except OSError as e:
        logger.error("Erro ao listar scripts: %s", e)
        sh_files = []

    if not sh_files:
        return jsonify({"message": "Nenhum script encontrado no servidor"}), 404

    script_path = os.path.join(SCRIPTS_DIR, sh_files[0])
    real_path = os.path.realpath(script_path)

    if not real_path.startswith(os.path.realpath(SCRIPTS_DIR)):
        return jsonify({"message": "Acesso negado"}), 403

    logger.info("Enviando script: %s", real_path)
    return send_file(real_path, as_attachment=True, download_name="ptr_inject.sh")


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=80)
