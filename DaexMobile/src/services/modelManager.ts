import RNFS from 'react-native-fs';
import DeviceInfo from 'react-native-device-info';

const MODEL_FILENAME = 'google_gemma-4-E4B-it-Q4_K_M.gguf';
const MODEL_DOWNLOAD_URL =
  'https://huggingface.co/bartowski/google_gemma-4-E4B-it-GGUF/resolve/main/google_gemma-4-E4B-it-Q4_K_M.gguf';

// Expected size in bytes (~2.5GB) — used for integrity check
const EXPECTED_MIN_SIZE = 2_000_000_000;

export interface DownloadProgress {
  bytesWritten: number;
  contentLength: number;
  percent: number;
}

export interface StorageInfo {
  freeSpace: number; // bytes
  totalRAM: number; // bytes
  modelSize: number; // bytes, 0 if not downloaded
  isDownloaded: boolean;
}

class ModelManager {
  private downloadJobId: number | null = null;

  /**
   * Returns the absolute path where the model file lives (or should live)
   * in the app's document directory.
   */
  getModelPath(): string {
    return `${RNFS.DocumentDirectoryPath}/${MODEL_FILENAME}`;
  }

  /**
   * Checks if the model file exists and is large enough to be valid.
   */
  async isModelDownloaded(): Promise<boolean> {
    const path = this.getModelPath();
    const exists = await RNFS.exists(path);
    if (!exists) return false;

    try {
      const stat = await RNFS.stat(path);
      return Number(stat.size) > EXPECTED_MIN_SIZE;
    } catch {
      return false;
    }
  }

  /**
   * Downloads the GGUF model from HuggingFace CDN with progress tracking.
   */
  async downloadModel(
    onProgress?: (progress: DownloadProgress) => void,
  ): Promise<string> {
    const destPath = this.getModelPath();

    // Clean up any partial download
    const exists = await RNFS.exists(destPath);
    if (exists) {
      const stat = await RNFS.stat(destPath);
      if (Number(stat.size) < EXPECTED_MIN_SIZE) {
        await RNFS.unlink(destPath);
      } else {
        // Already downloaded and valid
        return destPath;
      }
    }

    const downloadResult = RNFS.downloadFile({
      fromUrl: MODEL_DOWNLOAD_URL,
      toFile: destPath,
      background: true,
      discretionary: false,
      cacheable: false,
      progressDivider: 1,
      begin: (_res: any) => {
        // Download started
      },
      progress: (res: any) => {
        if (onProgress) {
          const percent =
            res.contentLength > 0
              ? Math.round((res.bytesWritten / res.contentLength) * 100)
              : 0;
          onProgress({
            bytesWritten: res.bytesWritten,
            contentLength: res.contentLength,
            percent,
          });
        }
      },
    });

    this.downloadJobId = downloadResult.jobId;

    const result = await downloadResult.promise;

    this.downloadJobId = null;

    if (result.statusCode !== 200) {
      // Clean up failed download
      const failedExists = await RNFS.exists(destPath);
      if (failedExists) {
        await RNFS.unlink(destPath);
      }
      throw new Error(
        `Download failed with status ${result.statusCode}`,
      );
    }

    return destPath;
  }

  /**
   * Cancel an in-progress download.
   */
  cancelDownload(): void {
    if (this.downloadJobId !== null) {
      RNFS.stopDownload(this.downloadJobId);
      this.downloadJobId = null;
    }
  }

  /**
   * Delete the model file from device storage.
   */
  async deleteModel(): Promise<void> {
    const path = this.getModelPath();
    const exists = await RNFS.exists(path);
    if (exists) {
      await RNFS.unlink(path);
    }
  }

  /**
   * Get storage and device info relevant to model management.
   */
  async getStorageInfo(): Promise<StorageInfo> {
    const [freeSpace, totalRAM, isDownloaded] = await Promise.all([
      RNFS.getFSInfo().then((info: any) => info.freeSpace),
      DeviceInfo.getTotalMemory(),
      this.isModelDownloaded(),
    ]);

    let modelSize = 0;
    if (isDownloaded) {
      try {
        const stat = await RNFS.stat(this.getModelPath());
        modelSize = Number(stat.size);
      } catch {
        // ignore
      }
    }

    return { freeSpace, totalRAM, modelSize, isDownloaded };
  }

  /**
   * Format bytes into a human-readable string.
   */
  formatBytes(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
  }
}

// Singleton export
export const modelManager = new ModelManager();
