import React, { useState, useRef, useEffect } from 'react';
import { FlatList, Platform, StyleSheet, TouchableOpacity, Keyboard } from 'react-native';
import { BlurView } from '@react-native-community/blur';
import { YStack, XStack, Text, Input, Button } from 'tamagui';
import { useAegisInference } from '../hooks/useAegisInference';
import { MessageLine } from './MessageLine';

interface Props {
  onBack: () => void;
}

export const MobileExecution: React.FC<Props> = ({ onBack }) => {
  const [inputText, setInputText] = useState('');
  const [keyboardHeight, setKeyboardHeight] = useState(0);
  const { messages, isGenerating, hardwareState, submitPrompt } = useAegisInference();
  const flatListRef = useRef<FlatList>(null);

  useEffect(() => {
    const showSubscription = Keyboard.addListener(
      Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow',
      (e) => setKeyboardHeight(e.endCoordinates.height)
    );
    const hideSubscription = Keyboard.addListener(
      Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide',
      () => setKeyboardHeight(0)
    );

    return () => {
      showSubscription.remove();
      hideSubscription.remove();
    };
  }, []);

  const handleSend = () => {
    if (inputText.trim() && !isGenerating) {
      submitPrompt(inputText);
      setInputText('');
    }
  };

  return (
    <YStack f={1} bg="#000000">
      <YStack flex={1}>
        {/* Header */}
        <XStack 
          ai="center" 
          jc="space-between" 
          paddingHorizontal="$4" 
          paddingTop="$6"
          paddingBottom="$3"
          borderBottomWidth={1}
          borderBottomColor="rgba(0, 255, 255, 0.1)"
        >
          <XStack ai="center">
            <TouchableOpacity onPress={onBack} style={{ marginRight: 16 }}>
              <Text color="#00ffff" fontSize={24}>←</Text>
            </TouchableOpacity>
            <Text color="#00ffff" fontSize={18} fontWeight="bold" letterSpacing={1}>
              AEGIS ENGINE
            </Text>
          </XStack>
          <XStack ai="center" bg="rgba(0, 255, 255, 0.1)" px="$3" py="$1.5" borderRadius="$4" borderWidth={1} borderColor="rgba(0, 255, 255, 0.3)">
            <YStack w={8} h={8} borderRadius={4} bg={hardwareState === 'NPU' ? "#00ffff" : "#f59e0b"} mr="$2" />
            <Text color="#e2e8f0" fontSize={12} fontFamily="monospace">
              {hardwareState === 'NPU' ? 'HEXAGON (MOCK)' : 'CPU'}
            </Text>
          </XStack>
        </XStack>

        {/* Chat Area */}
        <YStack f={1}>
          <FlatList
            ref={flatListRef}
            data={messages}
            keyExtractor={(item) => item.id}
            renderItem={({ item }) => <MessageLine message={item} />}
            contentContainerStyle={{ paddingVertical: 16, paddingBottom: 100 + keyboardHeight }}
            onContentSizeChange={() => flatListRef.current?.scrollToEnd({ animated: true })}
            onLayout={() => flatListRef.current?.scrollToEnd({ animated: true })}
          />
        </YStack>

        {/* Frosted Glass Input Area */}
        <YStack position="absolute" bottom={keyboardHeight} left={0} right={0} pb={Platform.OS === 'ios' ? "$6" : "$4"} overflow="hidden">
          <BlurView
            style={StyleSheet.absoluteFill}
            blurType="dark"
            blurAmount={15}
            reducedTransparencyFallbackColor="#000000"
          />
          
          <XStack 
            ai="center" 
            mx="$4" 
            mt="$3" 
            p="$1.5" 
            bg="transparent" 
          >
            <Input
              f={1}
              borderWidth={0}
              bg="transparent"
              color="#ffffff"
              fontSize={16}
              value={inputText}
              onChangeText={setInputText}
              placeholder="Send protocol to Aegis..."
              placeholderTextColor="rgba(255, 255, 255, 0.4)"
              onSubmitEditing={handleSend}
              editable={!isGenerating}
            />
            <Button
              size="$3"
              circular
              bg={isGenerating ? "rgba(0, 255, 255, 0.2)" : "#00ffff"}
              onPress={handleSend}
              disabled={isGenerating}
              ml="$2"
            >
              <YStack w={12} h={12} borderRadius={6} bg={isGenerating ? "rgba(0, 255, 255, 0.5)" : "#000000"} />
            </Button>
          </XStack>
        </YStack>
      </YStack>
    </YStack>
  );
};
