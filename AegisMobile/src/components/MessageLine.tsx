import React from 'react';
import { View, Text as RNText, StyleSheet } from 'react-native';
import { ChatMessage } from '../hooks/useAegisInference';
import { XStack, YStack, Text } from 'tamagui';
import Markdown from 'react-native-markdown-renderer';

interface Props {
  message: ChatMessage;
  isLastModel?: boolean;
  isGenerating?: boolean;
  tokenSpeed?: number;
}

const markdownStyles: any = {
  body: {
    color: '#e2e8f0',
    fontSize: 15,
    lineHeight: 24,
  },
  text: {
    color: '#e2e8f0',
    fontSize: 15,
    lineHeight: 24,
  },
  heading1: {
    color: '#ffffff',
    fontSize: 22,
    fontWeight: 'bold' as const,
    marginTop: 16,
    marginBottom: 8,
  },
  heading2: {
    color: '#ffffff',
    fontSize: 19,
    fontWeight: 'bold' as const,
    marginTop: 14,
    marginBottom: 6,
  },
  heading3: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '600' as const,
    marginTop: 12,
    marginBottom: 4,
  },
  paragraph: {
    color: '#e2e8f0',
    fontSize: 15,
    lineHeight: 24,
    marginTop: 0,
    marginBottom: 8,
  },
  strong: {
    color: '#ffffff',
    fontWeight: 'bold' as const,
  },
  em: {
    color: '#e2e8f0',
    fontStyle: 'italic' as const,
  },
  link: {
    color: '#00ffff',
    textDecorationLine: 'underline' as const,
  },
  blockquote: {
    borderLeftWidth: 3,
    borderLeftColor: 'rgba(0, 255, 255, 0.4)',
    paddingLeft: 12,
    marginLeft: 0,
    marginVertical: 8,
    backgroundColor: 'rgba(0, 255, 255, 0.04)',
    paddingVertical: 4,
    borderRadius: 4,
  },
  code_inline: {
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
    color: '#00ffff',
    fontFamily: 'monospace',
    fontSize: 13,
    paddingHorizontal: 5,
    paddingVertical: 2,
    borderRadius: 3,
  },
  code_block: {
    backgroundColor: 'rgba(255, 255, 255, 0.06)',
    color: '#e2e8f0',
    fontFamily: 'monospace',
    fontSize: 13,
    lineHeight: 20,
    padding: 12,
    borderRadius: 8,
    marginVertical: 8,
    borderWidth: 0.5,
    borderColor: 'rgba(255, 255, 255, 0.1)',
  },
  fence: {
    backgroundColor: 'rgba(255, 255, 255, 0.06)',
    color: '#e2e8f0',
    fontFamily: 'monospace',
    fontSize: 13,
    lineHeight: 20,
    padding: 12,
    borderRadius: 8,
    marginVertical: 8,
    borderWidth: 0.5,
    borderColor: 'rgba(255, 255, 255, 0.1)',
  },
  bullet_list: {
    marginVertical: 4,
  },
  ordered_list: {
    marginVertical: 4,
  },
  list_item: {
    color: '#e2e8f0',
    fontSize: 15,
    lineHeight: 24,
    marginBottom: 4,
  },
  hr: {
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
    height: 1,
    marginVertical: 12,
  },
  table: {
    borderWidth: 0.5,
    borderColor: 'rgba(255, 255, 255, 0.15)',
    borderRadius: 6,
    marginVertical: 8,
  },
  thead: {
    backgroundColor: 'rgba(0, 255, 255, 0.06)',
  },
  th: {
    color: '#00ffff',
    fontWeight: 'bold' as const,
    fontSize: 13,
    padding: 8,
    borderBottomWidth: 0.5,
    borderColor: 'rgba(255, 255, 255, 0.15)',
  },
  td: {
    color: '#e2e8f0',
    fontSize: 13,
    padding: 8,
    borderBottomWidth: 0.5,
    borderColor: 'rgba(255, 255, 255, 0.06)',
  },
};

export const MessageLine: React.FC<Props> = ({
  message,
  isLastModel,
  isGenerating,
  tokenSpeed,
}) => {
  const isUser = message.role === 'user';

  if (isUser) {
    // User messages keep the bubble style
    return (
      <XStack
        w="100%"
        justifyContent="flex-end"
        paddingHorizontal="$3"
        paddingVertical="$1.5"
      >
        <YStack
          maxWidth="80%"
          bg="rgba(0, 255, 255, 0.15)"
          paddingHorizontal="$4"
          paddingVertical="$3"
          borderRadius="$6"
          borderBottomRightRadius={0}
          borderWidth={1}
          borderColor="rgba(0, 255, 255, 0.3)"
        >
          <Text
            color="#00ffff"
            fontSize={15}
            lineHeight={22}
            fontFamily="monospace"
          >
            {message.content}
          </Text>
        </YStack>
      </XStack>
    );
  }

  // Model responses: plain RN Views so Markdown layout measures correctly
  return (
    <View style={styles.modelContainer}>
      {/* Model label */}
      <View style={styles.labelRow}>
        <RNText style={styles.labelText}>🛡️ AEGIS</RNText>
      </View>

      {/* Markdown rendered content */}
      {message.content ? (
        <Markdown style={markdownStyles}>
          {message.content}
        </Markdown>
      ) : (
        <RNText style={styles.cursor}>▊</RNText>
      )}

      {/* TPS metrics */}
      {isLastModel && tokenSpeed !== undefined && tokenSpeed > 0 && (
        <View style={styles.tpsRow}>
          <RNText style={[styles.tpsText, isGenerating && styles.tpsActive]}>
            ⚡ {tokenSpeed} tok/s
          </RNText>
          {isGenerating && (
            <RNText style={styles.tpsGenerating}>generating...</RNText>
          )}
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  modelContainer: {
    width: '100%',
    paddingHorizontal: 16,
    paddingTop: 8,
    paddingBottom: 12,
  },
  labelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  labelText: {
    color: 'rgba(0, 255, 255, 0.6)',
    fontSize: 11,
    fontFamily: 'monospace',
  },
  cursor: {
    color: 'rgba(255, 255, 255, 0.3)',
    fontSize: 14,
  },
  tpsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 8,
    paddingTop: 8,
    borderTopWidth: 0.5,
    borderTopColor: 'rgba(255, 255, 255, 0.06)',
    gap: 12,
  },
  tpsText: {
    color: 'rgba(255, 255, 255, 0.35)',
    fontSize: 10,
    fontFamily: 'monospace',
  },
  tpsActive: {
    color: '#a855f7',
  },
  tpsGenerating: {
    color: 'rgba(168, 85, 247, 0.6)',
    fontSize: 10,
    fontFamily: 'monospace',
  },
});
