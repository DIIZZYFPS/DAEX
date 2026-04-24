import React from 'react';
import { TouchableOpacity } from 'react-native';
import { YStack, Text } from 'tamagui';
import { DaexLogo } from './DaexLogo';

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
      {/* DAEX Logo */}
      <DaexLogo size={80} ambient />

      {/* Welcome Text */}
      <Text color="#ffffff" fontSize={22} fontWeight="bold" letterSpacing={3} mb="$2" mt="$4">
        Welcome to D A E X
      </Text>
      <Text color="rgba(255, 255, 255, 0.5)" fontSize={14} textAlign="center" mb="$6" lineHeight={22}>
        Icarus is ready. Execute with precision.
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
