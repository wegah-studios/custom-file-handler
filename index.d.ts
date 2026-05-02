declare module "custom-file-handler" {
  export type Filetype = "pdf" | "xlsx" | "zip";
  export type FileInfo = { uri: string; name: string; mime: string };

  export function pickDocument(type: Filetype): Promise<FileInfo>;
  export function saveFile(uri: string): Promise<FileInfo>;
  export function pickImage(): Promise<FileInfo>;
  export function takePhoto(): Promise<FileInfo>;

  const CustomFileHandler: {
    pickDocument: typeof pickDocument;
    saveFile: typeof saveFile;
    pickImage: typeof pickImage;
    takePhoto: typeof takePhoto;
  };

  export default CustomFileHandler;
}
