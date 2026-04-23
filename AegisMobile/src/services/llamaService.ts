import { initLlama, LlamaContext } from 'llama.rn';

export type ModelStatus = 'idle' | 'loading' | 'ready' | 'error';

interface LlamaServiceState {
  context: LlamaContext | null;
  status: ModelStatus;
  error: string | null;
  useGPU: boolean;
}

// Gemma 4 chat template tokens
const BOS = '<bos>';
const TURN_START = '<|turn>';
const TURN_END = '<turn|>';

const SYSTEM_PROMPT =
  'You are Aegis, a helpful AI assistant running directly on device hardware. You respond clearly and concisely.';

/**
 * Formats conversation history into Gemma 4's chat template.
 *
 * Template format:
 *   <bos><|turn>system {system}<turn|>
 *   <|turn>user {msg}<turn|>
 *   <|turn>model {msg}<turn|>
 *   ...
 *   <|turn>model\n
 */
function formatPrompt(
  messages: Array<{ role: 'user' | 'model'; content: string }>,
): string {
  let prompt = `${BOS}${TURN_START}system\n${SYSTEM_PROMPT}${TURN_END}\n`;

  for (const msg of messages) {
    prompt += `${TURN_START}${msg.role}\n${msg.content}${TURN_END}\n`;
  }

  // Open the model turn for generation
  prompt += `${TURN_START}model\n`;
  return prompt;
}

class LlamaService {
  private state: LlamaServiceState = {
    context: null,
    status: 'idle',
    error: null,
    useGPU: false,
  };

  getStatus(): ModelStatus {
    return this.state.status;
  }

  getError(): string | null {
    return this.state.error;
  }

  isLoaded(): boolean {
    return this.state.status === 'ready' && this.state.context !== null;
  }

  /**
   * Initialize the llama.rn context with a GGUF model file.
   * @param modelPath Absolute path to the .gguf file on device storage
   * @param useGPU Whether to offload layers to the GPU (Vulkan on Android)
   */
  async initContext(modelPath: string, useGPU: boolean = false): Promise<void> {
    if (this.state.context) {
      await this.releaseContext();
    }

    this.state.status = 'loading';
    this.state.error = null;
    this.state.useGPU = useGPU;

    try {
      const context = await initLlama({
        model: modelPath,
        n_ctx: 4096, // Context window — conservative for mobile RAM
        n_batch: 512,
        n_threads: 4, // Most mobile SoCs have 4 performance cores
        n_gpu_layers: useGPU ? 99 : 0, // 0 = CPU only, 99 = offload all layers
        use_mlock: true, // Lock model in RAM to prevent swapping
      });

      this.state.context = context;
      this.state.status = 'ready';
    } catch (err: any) {
      this.state.status = 'error';
      this.state.error = err?.message || 'Failed to load model';
      throw err;
    }
  }

  /**
   * Generate a streaming response from the model.
   * @param messages Conversation history
   * @param onToken Callback fired for each generated token
   * @returns Object with final text and performance stats
   */
  async generateResponse(
    messages: Array<{ role: 'user' | 'model'; content: string }>,
    onToken: (token: string) => void,
  ): Promise<{ text: string; tokensPerSecond: number }> {
    if (!this.state.context) {
      throw new Error('Model not loaded. Call initContext() first.');
    }

    const prompt = formatPrompt(messages);
    const startTime = Date.now();
    let tokenCount = 0;
    let fullText = '';

    const result = await this.state.context.completion(
      {
        prompt,
        n_predict: 512, // Max tokens to generate
        temperature: 0.7,
        top_p: 0.9,
        top_k: 40,
        stop: [TURN_END, '<eos>', '<end_of_turn>'], // Stop tokens for Gemma 4
      },
      (data: { token: string }) => {
        tokenCount++;
        fullText += data.token;
        onToken(data.token);
      },
    );

    const elapsedSeconds = (Date.now() - startTime) / 1000;
    const tokensPerSecond =
      elapsedSeconds > 0 ? tokenCount / elapsedSeconds : 0;

    return {
      text: result?.text || fullText,
      tokensPerSecond: Math.round(tokensPerSecond * 10) / 10,
    };
  }

  /**
   * Release the model context and free RAM.
   */
  async releaseContext(): Promise<void> {
    if (this.state.context) {
      try {
        await this.state.context.release();
      } catch (_err) {
        // Ignore release errors — context may already be freed
      }
      this.state.context = null;
    }
    this.state.status = 'idle';
    this.state.error = null;
  }
}

// Singleton export
export const llamaService = new LlamaService();
