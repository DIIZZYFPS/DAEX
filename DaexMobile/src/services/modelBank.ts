export interface Model {
    id: string;
    name: string;
    size: number;
    description: string;
    requiredRAM: number;
    downloadUrl: string;
}

export const MODEL_BANK: Model[] = [
    {
        id: 'gemma-4-E4B-it-Q4_K_M',
        name: 'Gemma 4-E4B-it-Q4_K_M',
        size: 2_500_000_000,
        description: 'Gemma 4-E4B-it-Q4_K_M is a 4-billion parameter language model that has been trained on a diverse range of tasks. It is a 4-bit quantized model, which means that it has been compressed to reduce its size and improve its performance. It is a 4-bit quantized model, which means that it has been compressed to reduce its size and improve its performance.',
        requiredRAM: 6_000_000_000,
        downloadUrl: 'https://huggingface.co/bartowski/google_gemma-4-E4B-it-GGUF/resolve/main/google_gemma-4-E4B-it-Q4_K_M.gguf',
    },

];