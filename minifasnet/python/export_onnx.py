"""One-off tool: export the two upstream MiniFASNet .pth weights to ONNX.

Source of truth for the architecture is the upstream repo (cloned read-only into
minifasnet-study/). We import its model definitions instead of copying them.

The exported graph outputs RAW LOGITS (3 classes), exactly like the PyTorch
forward(); softmax is applied afterwards by the caller, as the upstream repo does.

Usage:  python export_onnx.py [--repo PATH] [--out PATH]
"""
import argparse
import os
import sys

import numpy as np
import onnx
import onnxruntime as ort
import torch

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
DEFAULT_REPO = os.path.join(ROOT, "minifasnet-study", "Silent-Face-Anti-Spoofing")
DEFAULT_OUT = os.path.join(ROOT, "minifasnet", "models")


def export_one(repo, pth_name, out_dir):
    sys.path.insert(0, repo)
    from src.model_lib.MiniFASNet import (MiniFASNetV1, MiniFASNetV2,
                                          MiniFASNetV1SE, MiniFASNetV2SE)
    from src.utility import get_kernel, parse_model_name
    mapping = {"MiniFASNetV1": MiniFASNetV1, "MiniFASNetV2": MiniFASNetV2,
               "MiniFASNetV1SE": MiniFASNetV1SE, "MiniFASNetV2SE": MiniFASNetV2SE}

    h, w, model_type, scale = parse_model_name(pth_name)
    model = mapping[model_type](conv6_kernel=get_kernel(h, w))
    state = torch.load(os.path.join(repo, "resources", "anti_spoof_models", pth_name),
                       map_location="cpu")
    # Some checkpoints were saved from DataParallel ('module.' prefix) -- same handling as upstream.
    state = {(k[7:] if k.startswith("module.") else k): v for k, v in state.items()}
    model.load_state_dict(state)
    model.eval()

    onnx_path = os.path.join(out_dir, pth_name.replace(".pth", ".onnx"))
    dummy = torch.zeros(1, 3, h, w)
    torch.onnx.export(model, dummy, onnx_path, opset_version=17,
                      input_names=["input"], output_names=["logits"],
                      dynamic_axes={"input": {0: "batch"}, "logits": {0: "batch"}},
                      dynamo=False)
    onnx.checker.check_model(onnx.load(onnx_path))

    # Quick sanity: ONNX vs PyTorch on a few random 0..255 inputs (full parity is Step 4).
    sess = ort.InferenceSession(onnx_path, providers=["CPUExecutionProvider"])
    rng = np.random.default_rng(0)
    worst = 0.0
    for _ in range(5):
        x = rng.uniform(0, 255, (1, 3, h, w)).astype(np.float32)
        with torch.no_grad():
            ref = model(torch.from_numpy(x)).numpy()
        got = sess.run(None, {"input": x})[0]
        worst = max(worst, float(np.abs(ref - got).max()))
    n_params = sum(p.numel() for p in model.parameters())
    print(f"{pth_name}: type={model_type} scale={scale} input=(1,3,{h},{w}) "
          f"params={n_params} onnx={os.path.getsize(onnx_path)}B max|logit diff|={worst:.2e}")
    return onnx_path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=DEFAULT_REPO)
    ap.add_argument("--out", default=DEFAULT_OUT)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    models_dir = os.path.join(a.repo, "resources", "anti_spoof_models")
    for name in sorted(os.listdir(models_dir)):
        if name.endswith(".pth"):
            export_one(a.repo, name, a.out)


if __name__ == "__main__":
    main()
