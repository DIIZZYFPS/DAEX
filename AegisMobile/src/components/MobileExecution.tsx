import React, { useState, useRef, useEffect } from 'react';
import { FlatList, Platform, StyleSheet, TouchableOpacity, Keyboard, View } from 'react-native';
import { BlurView } from '@react-native-community/blur';
import { YStack, XStack, Text, Input, Button } from 'tamagui';
import { useAegisInference } from '../hooks/useAegisInference';
import { MessageLine } from './MessageLine';
import { Sidebar } from './Sidebar';
import { SettingsModal } from './SettingsModal';
import { SuggestedPrompts } from './SuggestedPrompts';

interface Props {
  onBack: () => void;
}

export const MobileExecution: React.FC<Props> = ({ onBack }) => {
  const [inputText, setInputText] = useState('');
  const [keyboardHeight, setKeyboardHeight] = useState(0);
  const [sidebarVisible, setSidebarVisible] = useState(false);
  const [settingsVisible, setSettingsVisible] = useState(false);
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

  const handleSend = (text?: string) => {
    const toSend = text || inputText;
    if (toSend.trim() && !isGenerating) {
      submitPrompt(toSend);
      setInputText('');
    }
  };

  const handleSelectPrompt = (prompt: string) => {
    handleSend(prompt);
  };

  const handleNewConversation = () => {
    setSidebarVisible(false);
    // Future: clear messages / start fresh session
  };

  const handleOpenSettings = () => {
    setSidebarVisible(false);
    setSettingsVisible(true);
  };

  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <YStack f={1} bg="#000000">
        <YStack flex={1}>
          {/* Header */}
          <XStack 
            ai="center" 
            jc="space-between" 
            paddingHorizontal="$4" 
            paddingTop="$6"
            paddingBottom="$3"
            borderBottomWidth={0.5}
            borderBottomColor="rgba(0, 255, 255, 0.15)"
          >
            <XStack ai="center">
              <TouchableOpacity
                onPress={() => setSidebarVisible(true)}
                style={{ marginRight: 16 }}
                hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
              >
                <Text color="#00ffff" fontSize={22}>☰</Text>
              </TouchableOpacity>
              <XStack ai="center" gap="$1.5">
                <Text color="#00ffff" fontSize={14}>🛡️</Text>
                <Text color="#ffffff" fontSize={16} fontWeight="bold" letterSpacing={2}>
                  AEGIS
                </Text>
              </XStack>
            </XStack>
            <XStack ai="center" gap="$3">
              <XStack ai="center" bg="rgba(0, 255, 255, 0.08)" px="$2.5" py="$1.5" borderRadius="$3" borderWidth={0.5} borderColor="rgba(0, 255, 255, 0.2)">
                <View style={{ width: 6, height: 6, borderRadius: 3, backgroundColor: hardwareState === 'NPU' ? '#00ffff' : '#f59e0b', marginRight: 6 }} />
                <Text color="#e2e8f0" fontSize={10} fontFamily="monospace">
                  {hardwareState === 'NPU' ? 'NPU' : 'CPU'}
                </Text>
              </XStack>
              <TouchableOpacity
                onPress={handleNewConversation}
                hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
              >
                <Text color="#00ffff" fontSize={22}>+</Text>
              </TouchableOpacity>
            </XStack>
          </XStack>

          {/* Chat Area or Welcome */}
          {messages.length === 0 ? (
            <SuggestedPrompts onSelectPrompt={handleSelectPrompt} />
          ) : (
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
          )}

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
                onSubmitEditing={() => handleSend()}
                editable={!isGenerating}
              />
              <Button
                size="$3"
                circular
                bg={isGenerating ? "rgba(0, 255, 255, 0.2)" : "#00ffff"}
                onPress={() => handleSend()}
                disabled={isGenerating}
                ml="$2"
              >
                <YStack w={12} h={12} borderRadius={6} bg={isGenerating ? "rgba(0, 255, 255, 0.5)" : "#000000"} />
              </Button>
            </XStack>
          </YStack>
        </YStack>
      </YStack>

      {/* Overlays */}
      <Sidebar
        visible={sidebarVisible}
        onClose={() => setSidebarVisible(false)}
        onNewConversation={handleNewConversation}
        onOpenSettings={handleOpenSettings}
      />
      <SettingsModal
        visible={settingsVisible}
        onClose={() => setSettingsVisible(false)}
      />
    </View>
  );
};
