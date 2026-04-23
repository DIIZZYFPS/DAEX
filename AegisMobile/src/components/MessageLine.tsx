import React from 'react';
import { ChatMessage } from '../hooks/useAegisInference';
import { XStack, YStack, Text } from 'tamagui';

interface Props {
  message: ChatMessage;
}

export const MessageLine: React.FC<Props> = ({ message }) => {
  const isUser = message.role === 'user';

  return (
    <XStack 
      w="100%" 
      justifyContent={isUser ? "flex-end" : "flex-start"} 
      paddingHorizontal="$3" 
      paddingVertical="$1.5"
    >
      <YStack
        maxWidth="80%"
        bg={isUser ? "rgba(0, 255, 255, 0.15)" : "rgba(255, 255, 255, 0.05)"}
        paddingHorizontal="$4"
        paddingVertical="$3"
        borderRadius="$6"
        borderBottomRightRadius={isUser ? 0 : "$6"}
        borderBottomLeftRadius={isUser ? "$6" : 0}
        borderWidth={1}
        borderColor={isUser ? "rgba(0, 255, 255, 0.3)" : "rgba(255, 255, 255, 0.1)"}
      >
        <Text 
          color={isUser ? "#00ffff" : "#e2e8f0"} 
          fontSize={15} 
          lineHeight={22}
          fontFamily={isUser ? "monospace" : "System"}
        >
          {message.content}
        </Text>
      </YStack>
    </XStack>
  );
};
