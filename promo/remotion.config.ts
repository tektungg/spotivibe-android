import { Config } from "@remotion/cli/config";

// JPEG frames keep the render fast; CRF 16 keeps the hairlines crisp.
Config.setVideoImageFormat("jpeg");
Config.setJpegQuality(95);
Config.setCodec("h264");
Config.setCrf(16);
Config.setPixelFormat("yuv420p");
Config.setAudioCodec("aac");
// ANGLE lets Chrome use the GPU for the CSS blur and 3D transforms.
Config.setChromiumOpenGlRenderer("angle");
Config.setOverwriteOutput(true);
