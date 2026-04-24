import { useState, useCallback, useRef, useEffect } from 'react';
import { llamaService } from '../services/llamaService';
import { modelManager, DownloadProgress } from '../services/modelManager';
import { Model } from '../services/modelBank';

export type Role = 'user' | 'model';


export type ModelStatus =
  | 'not_downloaded'
  | 'downloading'
  | 'loading'
  | 'ready'
  | 'error';

export interface ChatMessage {
  id: string;
  role: Role;
  content: string;
}

export const useDaexInference = (model: Model) => {

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isGenerating, setIsGenerating] = useState<boolean>(false);
  const [hardwareState, setHardwareState] = useState<'GPU' | 'CPU'>('CPU');
  const [modelStatus, setModelStatus] = useState<ModelStatus>('not_downloaded');
  const [downloadProgress, setDownloadProgress] = useState<number>(0);
  const [tokenSpeed, setTokenSpeed] = useState<number>(0);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [useGPU, setUseGPU] = useState<boolean>(false);
  const abortRef = useRef<boolean>(false);

  // Check model status on mount
  useEffect(() => {
    checkModelStatus();
  }, []);

  const checkModelStatus = useCallback(async () => {
    try {
      const isDownloaded = await modelManager.isModelDownloaded(model);
      if (isDownloaded) {
        if (llamaService.isLoaded()) {
          setModelStatus('ready');
        } else {
          setModelStatus('not_downloaded'); // Downloaded but not loaded — show as available
          // Actually set a different label:
          setModelStatus('not_downloaded');
        }
      } else {
        setModelStatus('not_downloaded');
      }
    } catch {
      setModelStatus('not_downloaded');
    }
  }, []);

  /**
   * Download the model from HuggingFace CDN.
   */
  const downloadModel = useCallback(async () => {
    if (modelStatus === 'downloading') return;

    setModelStatus('downloading');
    setDownloadProgress(0);
    setErrorMessage(null);

    try {
      await modelManager.downloadModel(model, (progress: DownloadProgress) => {
        setDownloadProgress(progress.percent);
      });
      // Download complete — don't auto-load, let user trigger via loadModel
      setModelStatus('not_downloaded'); // Will show as "ready to load"
      setDownloadProgress(100);
    } catch (err: any) {
      setModelStatus('error');
      setErrorMessage(err?.message || 'Download failed');
    }
  }, [modelStatus]);

  /**
   * Cancel an in-progress download.
   */
  const cancelDownload = useCallback(() => {
    modelManager.cancelDownload();
    setModelStatus('not_downloaded');
    setDownloadProgress(0);
  }, []);

  /**
   * Load the model into memory from device storage.
   */
  const loadModel = useCallback(async () => {
    const isDownloaded = await modelManager.isModelDownloaded(model);

    if (!isDownloaded) {
      // Need to download first
      setModelStatus('downloading');
      setDownloadProgress(0);
      setErrorMessage(null);

      try {
        await modelManager.downloadModel(model, (progress: DownloadProgress) => {
          setDownloadProgress(progress.percent);
        });
      } catch (err: any) {
        setModelStatus('error');
        setErrorMessage(err?.message || 'Download failed');
        return;
      }
    }

    // Now load the model
    setModelStatus('loading');
    setErrorMessage(null);

    try {
      const modelPath = modelManager.getModelPath(model);
      await llamaService.initContext(modelPath, useGPU);
      setModelStatus('ready');
      setHardwareState(useGPU ? 'GPU' : 'CPU');
    } catch (err: any) {
      setModelStatus('error');
      setErrorMessage(err?.message || 'Failed to load model');
    }
  }, [useGPU]);

  /**
   * Unload the model from memory.
   */
  const unloadModel = useCallback(async () => {
    await llamaService.releaseContext();
    setModelStatus('not_downloaded');
    setTokenSpeed(0);
  }, []);

  /**
   * Toggle GPU offload setting. Requires model reload to take effect.
   */
  const toggleGPU = useCallback(
    async (enabled: boolean) => {
      setUseGPU(enabled);

      // If model is loaded, reload with new GPU setting
      if (llamaService.isLoaded()) {
        setModelStatus('loading');
        try {
          await llamaService.releaseContext();
          const modelPath = modelManager.getModelPath(model);
          await llamaService.initContext(modelPath, enabled);
          setModelStatus('ready');
          setHardwareState(enabled ? 'GPU' : 'CPU');
        } catch (err: any) {
          setModelStatus('error');
          setErrorMessage(err?.message || 'Failed to reload model');
        }
      }
    },
    [],
  );

  /**
   * Send a prompt and stream the response from the on-device model.
   */
  const submitPrompt = useCallback(
    async (prompt: string) => {
      if (!prompt.trim() || isGenerating) return;
      if (modelStatus !== 'ready' || !llamaService.isLoaded()) {
        setErrorMessage('Model is not loaded yet.');
        return;
      }

      abortRef.current = false;

      // Add user message
      const userMsg: ChatMessage = {
        id: Date.now().toString(),
        role: 'user',
        content: prompt,
      };

      // Create empty model response placeholder
      const modelMsgId = (Date.now() + 1).toString();
      const modelMsg: ChatMessage = {
        id: modelMsgId,
        role: 'model',
        content: '',
      };

      setMessages((prev) => [...prev, userMsg, modelMsg]);
      setIsGenerating(true);
      setTokenSpeed(0);

      try {
        // Build conversation history for the model (all messages including the new user message)
        const conversationHistory = [
          ...messages.map((m) => ({ role: m.role, content: m.content })),
          { role: 'user' as const, content: prompt },
        ];

        const result = await llamaService.generateResponse(
          conversationHistory,
          (token: string) => {
            if (abortRef.current) return;

            setMessages((prev) =>
              prev.map((msg) =>
                msg.id === modelMsgId
                  ? { ...msg, content: msg.content + token }
                  : msg,
              ),
            );
          },
        );

        setTokenSpeed(result.tokensPerSecond);
      } catch (err: any) {
        // Update the model message with the error
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === modelMsgId
              ? {
                  ...msg,
                  content:
                    msg.content || `[Error: ${err?.message || 'Generation failed'}]`,
                }
              : msg,
          ),
        );
      } finally {
        setIsGenerating(false);
      }
    },
    [isGenerating, messages, modelStatus],
  );

  /**
   * Clear all messages and start a new conversation.
   */
  const clearMessages = useCallback(() => {
    setMessages([]);
    setTokenSpeed(0);
  }, []);

  return {
    // Existing public API (unchanged)
    messages,
    isGenerating,
    hardwareState,
    submitPrompt,

    // New additions
    modelStatus,
    downloadProgress,
    tokenSpeed,
    errorMessage,
    useGPU,
    loadModel,
    unloadModel,
    downloadModel,
    cancelDownload,
    toggleGPU,
    clearMessages,
  };
};
