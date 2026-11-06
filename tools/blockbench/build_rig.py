"""Builds rig.bbmodel: a Blockbench (Bedrock format) preview rig matching Player Animation Library's player.

Bones (Blockbench pixels; the model faces north, -Z; the player's right is +X), as PAL animates them:
    body (0,12,0)                    whole player
      torso (0,24,0), head (0,24,0)
      right_arm (5,22,0) > right_item, left_arm (-5,22,0) > left_item
      right_leg (2,12,0), left_leg (-2,12,0)
Geometry, UVs and the Steve texture come from Emotecraft's template (KosmX/emotes blender/model_item.bbmodel).
The template is NOT part of this repository (it belongs to the Emotecraft project): it is downloaded into
run/blockbench/ (gitignored) the first time. Run: python3 -I tools/blockbench/build_rig.py  (writes
run/blockbench/rig.bbmodel)
"""
import json
import urllib.request
import uuid
from pathlib import Path

WORK = Path(__file__).resolve().parents[2] / "run" / "blockbench"
WORK.mkdir(parents=True, exist_ok=True)
TEMPLATE_URL = "https://raw.githubusercontent.com/KosmX/emotes/dev/blender/model_item.bbmodel"
if not (WORK / "model_item.bbmodel").exists():
    urllib.request.urlretrieve(TEMPLATE_URL, WORK / "model_item.bbmodel")
HERE = WORK
template = json.loads((HERE / "model_item.bbmodel").read_text())
by_name = {}
for e in template["elements"]:
    by_name.setdefault(e["name"], []).append(e)


def uid():
    return str(uuid.uuid4())


elements = []


def cube(name, frm, to, faces, inflate=0.0):
    e = {"name": name, "box_uv": False, "render_order": "default", "locked": False, "allow_mirror_modeling": False,
         "from": frm, "to": to, "autouv": 0, "color": 0, "origin": [0, 0, 0], "type": "cube", "uuid": uid(),
         "faces": {k: {"uv": v, "texture": 0} for k, v in faces.items()}}
    if inflate:
        e["inflate"] = inflate
    elements.append(e)
    return e["uuid"]


def faces_of(e):
    return {k: v["uv"] for k, v in e["faces"].items()}


groups = []


def group(name, origin, children):
    g = {"uuid": uid(), "export": True, "locked": False, "origin": origin, "rotation": [0, 0, 0], "color": 0,
         "name": name, "children": [], "reset": False, "shade": True, "mirror_uv": False, "selected": False,
         "visibility": True, "autouv": 0, "isOpen": True, "primary_selected": False}
    groups.append(g)
    return {"uuid": g["uuid"], "isOpen": True, "children": children}


head = group("head", [0, 24, 0], [cube("head", e["from"], e["to"], faces_of(e), e.get("inflate", 0)) for e in by_name["head"] + by_name["headwear"]])
torso = group("torso", [0, 24, 0], [cube("torso", e["from"], e["to"], faces_of(e), e.get("inflate", 0)) for e in by_name["torso"] + by_name["jacket"]])


def limb(name, pivot, item=None):
    children = [cube(name, e["from"], e["to"], faces_of(e), e.get("inflate", 0)) for e in by_name[name]]
    if item:
        children.append(group(item[0], item[1], []))
    return group(name, pivot, children)


right_arm = limb("right_arm", [5, 22, 0], ("right_item", [6, 12, -2]))
left_arm = limb("left_arm", [-5, 22, 0], ("left_item", [-6, 12, -2]))
right_leg = limb("right_leg", [2, 12, 0])
left_leg = limb("left_leg", [-2, 12, 0])
body = group("body", [0, 12, 0], [torso, head, right_arm, left_arm, right_leg, left_leg])

texture = dict(template["textures"][0])
model = {
    "meta": {"format_version": "5.0", "model_format": "bedrock", "box_uv": False},
    "name": "pal_player_rig",
    "model_identifier": "pal_player_rig",
    "visible_box": [1, 2, 0],
    "variable_placeholders": "",
    "variable_placeholder_buttons": [],
    "timeline_setups": [],
    "unhandled_root_fields": {},
    "resolution": {"width": 64, "height": 64},
    "elements": elements,
    "groups": groups,
    "outliner": [body],
    "textures": [texture],
}
(HERE / "rig.bbmodel").write_text(json.dumps(model))
print("rig.bbmodel:", len(elements), "cubes,", len(groups), "bones")
