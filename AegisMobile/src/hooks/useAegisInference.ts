import { useState, useCallback, useRef } from 'react';

export type Role = 'user' | 'model';

export interface ChatMessage {
  id: string;
  role: Role;
  content: string;
}

export const useAegisInference = () => {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isGenerating, setIsGenerating] = useState<boolean>(false);
  const [hardwareState, setHardwareState] = useState<'NPU' | 'CPU'>('NPU');
  const timerRef = useRef<NodeJS.Timeout | null>(null);

  const mockStreamingResponse = "This is a mock response from the Hexagon NPU. Eventually, this stream will be piped via JSI from the ExecuTorch C++ backend executing the Gemma 4 E4B tensor graph.\n\nOffline execution verified. Measured inference speed: 38.4 tokens/sec.";

  const submitPrompt = useCallback((prompt: string) => {
    if (!prompt.trim() || isGenerating) return;

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
      content: '', // Will stream into this
    };

    setMessages(prev => [...prev, userMsg, modelMsg]);
    setIsGenerating(true);

    // Mock Token Streaming
    let tokenIndex = 0;
    const tokens = mockStreamingResponse.split(' '); // simple word-based splitting for mock

    timerRef.current = setInterval(() => {
      if (tokenIndex < tokens.length) {
        const nextWord = tokens[tokenIndex] + ' ';
        setMessages(prev => 
          prev.map(msg => 
            msg.id === modelMsgId 
              ? { ...msg, content: msg.content + nextWord } 
              : msg
          )
        );
        tokenIndex++;
      } else {
        if (timerRef.current) clearInterval(timerRef.current);
        setIsGenerating(false);
      }
    }, 50); // 50ms per token

  }, [isGenerating]);

  return {
    messages,
    isGenerating,
    hardwareState,
    submitPrompt,
  };
};
