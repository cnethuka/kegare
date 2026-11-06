// Preview renderer for the PAL rig, injected into Blockbench through cdp.py.
// window.kegarePreview.load(bedrockAnimJson, name) -> imports an animation and selects it
// window.kegarePreview.frames(name, times[], views[]) -> [[dataURL per view] per time]
(() => {
  const W = 360, H = 480;
  const renderer = new THREE.WebGLRenderer({antialias: true, preserveDrawingBuffer: true, alpha: false});
  renderer.setSize(W, H);
  renderer.setClearColor(0x2b2f3a, 1);
  renderer.outputColorSpace = THREE.SRGBColorSpace || renderer.outputColorSpace;
  const camera = new THREE.PerspectiveCamera(32, W / H, 1, 4000);
  // Views: the model faces north (-Z); its right is +X. Angles are yaw around the model, 0 = straight at its face.
  const VIEWS = {
    front: [0, 6], three_quarter: [-35, 8], side: [-90, 4], back_three_quarter: [-145, 10], other_side: [90, 4], low: [-25, -12],
  };
  function shoot(view) {
    const [yawDeg, pitchDeg] = VIEWS[view] || VIEWS.three_quarter;
    const yaw = yawDeg * Math.PI / 180, pitch = pitchDeg * Math.PI / 180, dist = 92;
    const target = new THREE.Vector3(0, 17, 0);
    camera.position.set(target.x + Math.sin(yaw) * Math.cos(pitch) * dist, target.y + Math.sin(pitch) * dist,
                        target.z - Math.cos(yaw) * Math.cos(pitch) * dist);
    camera.lookAt(target);
    const hidden = [];
    for (const child of scene.children) {
      if (child === Project.model_3d || child.isLight) continue;
      if (child.visible) { hidden.push(child); child.visible = false; }
    }
    renderer.render(scene, camera);
    hidden.forEach(c => c.visible = true);
    return renderer.domElement.toDataURL('image/png');
  }
  window.kegarePreview = {
    load(json, name) {
      if (!Modes.selected || Modes.selected.id !== 'animate') Modes.options.animate.select();
      Animation.all.filter(a => a.name === name).forEach(a => a.remove(false));
      Animator.loadFile({path: name + '.animation.json', content: JSON.stringify(json)});
      const anim = Animation.all.find(a => a.name === name);
      anim.select();
      return {name: anim.name, length: anim.length, animators: Object.keys(anim.animators).length};
    },
    frames(name, times, views) {
      const anim = Animation.all.find(a => a.name === name);
      anim.select();
      const out = [];
      for (const t of times) {
        Timeline.setTime(t);
        Animator.preview();
        out.push(views.map(shoot));
      }
      Timeline.setTime(0);
      Animator.preview();
      return out;
    },
    rest(views) {
      Animation.selected && Animation.selected.unselect && Animation.selected.unselect();
      Canvas.updateAllBones && Canvas.updateAllBones();
      return views.map(shoot);
    },
  };
  return Object.keys(window.kegarePreview);
})()
