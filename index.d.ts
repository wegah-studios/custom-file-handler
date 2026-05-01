export type ImageResult = {
  uri: string;
};

export declare function pickImage(): Promise<string>;
export declare function takePhoto(): Promise<string>;

declare const _default: {
  pickImage: typeof pickImage;
  takePhoto: typeof takePhoto;
};

export default _default;