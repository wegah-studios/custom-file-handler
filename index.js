import { NativeModules, Platform } from "react-native";

const LINKING_ERROR =
  `The package 'custom-file-handler' doesn't seem to be linked properly.\n\n` +
  Platform.select({
    android: "- Run 'npx react-native run-android'\n",
    default: "",
  });

const CustomFileHandler = NativeModules.CustomFileHandler
  ? NativeModules.CustomFileHandler
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      },
    );

function ensureAndroid() {
  if (Platform.OS !== "android") {
    throw new Error("custom-file-handler works only on Android");
  }
}

export async function pickDocument(type) {
  ensureAndroid();
  return CustomFileHandler.pickDocument(type);
}
export async function saveFile(uri) {
  ensureAndroid();
  return CustomFileHandler.saveFile(uri);
}
export async function pickImage() {
  ensureAndroid();
  return CustomFileHandler.pickImage();
}
export async function takePhoto() {
  ensureAndroid();
  return CustomFileHandler.takePhoto();
}

export default {
  pickDocument,
  saveFile,
  pickImage,
  takePhoto,
};
