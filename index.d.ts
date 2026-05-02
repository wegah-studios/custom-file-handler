declare module "custom-file-handler" {
  export type Filetype = "pdf" | "xlsx" | "zip"

  export function pickDocument(type:Filetype):Promise<string>;
  export function saveFile(uri:string):Promise<string>;
  export function pickImage():Promise<string>;
  export function takePhoto():Promise<string>;


  const CustomFileHandler: {
    pickDocument: typeof pickDocument;
    saveFile: typeof saveFile;
    pickImage: typeof pickImage;
    takePhoto: typeof takePhoto;
  };

  export default CustomFileHandler;
}
