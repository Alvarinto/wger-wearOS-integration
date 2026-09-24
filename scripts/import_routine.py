#!/usr/bin/env python3
"""
Script de utilidad para importar rutinas estructuradas en formato JSON
directamente a la instancia de wger (vía API REST v2).
Lee credenciales de 'local.properties'.
"""

import sys
import json
import urllib.request
import urllib.error
import datetime
from pathlib import Path

def main():
    json_path = Path(sys.argv[1] if len(sys.argv) > 1 else "rutina.json")
    if not json_path.exists():
        print(f"❌ Error: El archivo '{json_path}' no existe.")
        sys.exit(1)

    # 1. Leer credenciales desde local.properties
    props_path = Path("local.properties")
    if not props_path.exists():
        print("❌ Error: No se encontró 'local.properties'. Crea uno con wger.server.url y wger.api.token.")
        sys.exit(1)

    props = {}
    with open(props_path) as f:
        for line in f:
            line = line.strip()
            if "=" in line and not line.startswith("#"):
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip().strip('"')

    server_url = props.get("wger.server.url", "").rstrip("/")
    api_token = props.get("wger.api.token", "")

    if not server_url or not api_token:
        print("❌ Error: 'wger.server.url' o 'wger.api.token' no están definidos en local.properties.")
        sys.exit(1)

    base_api = f"{server_url}/api/v2"
    headers = {
        "Authorization": f"Token {api_token}",
        "Content-Type": "application/json"
    }

    def api_post(endpoint, payload):
        req = urllib.request.Request(f"{base_api}/{endpoint}/", data=json.dumps(payload).encode(), headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req) as resp:
                return json.loads(resp.read().decode())
        except urllib.error.HTTPError as e:
            print(f"❌ Error en {endpoint}: {e.code} - {e.read().decode()}")
            raise

    # 2. Cargar JSON
    with open(json_path) as f:
        data = json.load(f)

    # Mapa de ejercicios estándar en wger
    exercise_map = {
        "inverted row": 1198,
        "push-up": 1551,
        "bench dip": 197,
        "plank": 1317,
        "dead bug": 178,
        "running": 527,
        "bulgarian split squat": 1706,
        "single leg glute bridge": 1740,
        "chin-up": 154,
        "calf raises": 622,
        "side plank": 1321
    }

    start_date = datetime.date.today().isoformat()
    end_date = (datetime.date.today() + datetime.timedelta(days=90)).isoformat()

    # wger tiene longitud máxima de 25 caracteres para el campo 'name' de rutina
    routine_name = data.get("routine_name", "Mi Rutina")[:25]
    description = data.get("description", "")

    print(f"🚀 Creando rutina '{routine_name}' en {server_url}...")
    routine = api_post("routine", {
        "name": routine_name,
        "description": description,
        "start": start_date,
        "end": end_date
    })
    routine_id = routine["id"]
    print(f"✅ Rutina ID: {routine_id} creada exitosamente.")

    # 3. Crear Días, Slots y Configuración de Series
    for day_idx, day_data in enumerate(data.get("days", []), start=1):
        day_name = day_data.get("day_name", f"Día {day_idx}")[:20]
        day = api_post("day", {
            "routine": routine_id,
            "name": day_name,
            "order": day_idx,
            "description": day_data.get("day_name", "")
        })
        day_id = day["id"]
        print(f"\n  📅 Día #{day_idx}: '{day_name}'")

        for ex_idx, ex in enumerate(day_data.get("exercises", []), start=1):
            ex_name = ex.get("name", "Ejercicio")
            ex_id = exercise_map.get(ex_name.lower(), 1551)
            comment = f"{ex_name}: {ex.get('notes', '')}" if ex.get("notes") else ex_name

            slot = api_post("slot", {
                "day": day_id,
                "order": ex_idx,
                "comment": comment[:200]
            })
            slot_id = slot["id"]

            entry = api_post("slot-entry", {
                "slot": slot_id,
                "exercise": ex_id,
                "type": "normal"
            })
            entry_id = entry["id"]

            sets = ex.get("sets", 3)
            reps = ex.get("reps", 10)
            rest = ex.get("rest_seconds", 60)

            api_post("sets-config", {"slot_entry": entry_id, "iteration": 1, "value": sets})
            api_post("repetitions-config", {"slot_entry": entry_id, "iteration": 1, "value": reps})
            api_post("rest-config", {"slot_entry": entry_id, "iteration": 1, "value": rest})

            print(f"    🏋️ {ex_name} -> {sets} series x {reps} reps (descanso: {rest}s)")

    print(f"\n🎉 ¡Rutina importada con éxito! Abre la app móvil y pulsa 'Obtener Rutinas de wger'.")

if __name__ == "__main__":
    main()
