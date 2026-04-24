import RNFS from 'react-native-fs';
import DeviceInfo from 'react-native-device-info';
import { Model } from './modelBank';

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
  isHardwareCapable: boolean;
}

class ModelManager {
  private downloadJobId: number | null = null;

  /**
   * Returns the absolute path where the model file lives.
   */
  getModelPath(model: Model): string {
    return `${RNFS.DocumentDirectoryPath}/${model.id}.gguf`;
  }

  /**
   * Checks if the device meets the hardware requirements for a specific model.
   */
  async checkSpecSupport(model: Model) {
    const [totalRAM, diskInfo] = await Promise.all([
      DeviceInfo.getTotalMemory(),
      RNFS.getFSInfo(),
    ]);

    const hasEnoughRAM = totalRAM >= model.requiredRAM;
    const hasEnoughStorage = diskInfo.freeSpace > model.size;

    return {
      totalRAM,
      freeSpace: diskInfo.freeSpace,
      hasEnoughRAM,
      hasEnoughStorage,
      supported: hasEnoughRAM, // Storage is transient, but RAM is a hard limit
    };
  }

  /**
   * Checks if the model file exists and is valid based on its expected size.
   */
  async isModelDownloaded(model: Model): Promise<boolean> {
    const path = this.getModelPath(model);
    const exists = await RNFS.exists(path);
    if (!exists) return false;

    try {
      const stat = await RNFS.stat(path);
      // Ensure the file is at least 90% of expected size to be considered "valid"
      return Number(stat.size) >= model.size * 0.9;
    } catch {
      return false;
    }
  }

  /**
   * Downloads a model from its bank URL with progress tracking.
   */
  async downloadModel(
    model: Model,
    onProgress?: (progress: DownloadProgress) => void,
  ): Promise<string> {
    const destPath = this.getModelPath(model);

    // Check hardware capability before starting
    const spec = await this.checkSpecSupport(model);
    if (!spec.supported) {
      throw new Error(
        `Device does not meet the required ${this.formatBytes(
          model.requiredRAM,
        )} of RAM for this model.`,
      );
    }

    // Clean up any partial download
    const exists = await RNFS.exists(destPath);
    if (exists) {
      const stat = await RNFS.stat(destPath);
      if (Number(stat.size) < model.size * 0.9) {
        await RNFS.unlink(destPath);
      } else {
        return destPath;
      }
    }

    const downloadResult = RNFS.downloadFile({
      fromUrl: model.downloadUrl,
      toFile: destPath,
      background: true,
      discretionary: false,
      cacheable: false,
      progressDivider: 1,
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
      if (await RNFS.exists(destPath)) {
        await RNFS.unlink(destPath);
      }
      throw new Error(`Download failed with status ${result.statusCode}`);
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
   * Delete a model file from device storage.
   */
  async deleteModel(model: Model): Promise<void> {
    const path = this.getModelPath(model);
    if (await RNFS.exists(path)) {
      await RNFS.unlink(path);
    }
  }

  /**
   * Get storage and device info relevant to a specific model.
   */
  async getStorageInfo(model: Model): Promise<StorageInfo> {
    const [spec, isDownloaded] = await Promise.all([
      this.checkSpecSupport(model),
      this.isModelDownloaded(model),
    ]);

    let modelSize = 0;
    if (isDownloaded) {
      try {
        const stat = await RNFS.stat(this.getModelPath(model));
        modelSize = Number(stat.size);
      } catch {}
    }

    return {
      freeSpace: spec.freeSpace,
      totalRAM: spec.totalRAM,
      modelSize,
      isDownloaded,
      isHardwareCapable: spec.supported,
    };
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

export const modelManager = new ModelManager();

