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

export function pickDocument(type) {
  CustomFileHandler.pickDocument(type);
}
export function saveFile(uri) {
  CustomFileHandler.saveFile(uri);
}
export function pickImage() {
  CustomFileHandler.pickImage();
}
export function takePhoto() {
  CustomFileHandler.takePhoto();
}

export default {
  pickDocument,
  saveFile,
  pickImage,
  takePhoto,
};
