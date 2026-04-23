import React from 'react';
import { TouchableOpacity } from 'react-native';
import { YStack, Text } from 'tamagui';

interface Props {
  onSelectPrompt: (prompt: string) => void;
}

const PROMPTS = [
  'Explain quantum entanglement simply',
  'Write a haiku about midnight code',
  'Plan a 3-day trip to Lisbon',
];

export const SuggestedPrompts: React.FC<Props> = ({ onSelectPrompt }) => {
  return (
    <YStack f={1} ai="center" jc="center" px="$4">
      {/* Shield Icon */}
      <Text fontSize={48} mb="$4">🛡️</Text>

      {/* Welcome Text */}
      <Text color="#ffffff" fontSize={22} fontWeight="bold" letterSpacing={3} mb="$2">
        Welcome to A E G I S
      </Text>
      <Text color="rgba(255, 255, 255, 0.5)" fontSize={14} textAlign="center" mb="$6" lineHeight={22}>
        Your intelligent companion. Ask anything — I'll respond with clarity.
      </Text>

      {/* Prompt Cards */}
      <YStack w="100%" gap="$2">
        {PROMPTS.map((prompt, index) => (
          <TouchableOpacity
            key={index}
            onPress={() => onSelectPrompt(prompt)}
            activeOpacity={0.6}
          >
            <YStack
              px="$4"
              py="$3.5"
              borderRadius="$4"
              borderWidth={0.5}
              borderColor="rgba(0, 255, 255, 0.2)"
              bg="rgba(255, 255, 255, 0.03)"
            >
              <Text color="rgba(255, 255, 255, 0.7)" fontSize={14}>
                {prompt}
              </Text>
            </YStack>
          </TouchableOpacity>
        ))}
      </YStack>
    </YStack>
  );
};
