import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'custom-image-picker' doesn't seem to be linked properly.\n\n` +
  Platform.select({
    android: "- Run 'npx react-native run-android'\n",
    default: '',
  });

const CustomImagePicker = NativeModules.CustomImagePicker
  ? NativeModules.CustomImagePicker
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export function pickImage() {
  return CustomImagePicker.pickImage();
}

export function takePhoto() {
  return CustomImagePicker.takePhoto();
}

export default {
  pickImage,
  takePhoto,
};