import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';

interface Props {
  onInitialize: () => void;
}

export const LandingPage: React.FC<Props> = ({ onInitialize }) => {
  return (
    <View style={styles.container}>
      <View style={styles.content}>
        <View style={styles.logoContainer}>
          <Text style={styles.title}>AEGIS</Text>
          <Text style={styles.subtitle}>BARE-METAL INTELLIGENCE</Text>
        </View>
        <Text style={styles.description}>
          Edge-optimized neural processing engine for Snapdragon Hexagon architecture.
        </Text>
      </View>
      <View style={styles.footer}>
        <TouchableOpacity 
          style={styles.initButton}
          onPress={onInitialize}
        >
          <Text style={styles.initText}>INITIALIZE PROTOCOL</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000000', justifyContent: 'space-between', padding: 24 },
  content: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  logoContainer: { alignItems: 'center', marginBottom: 24, padding: 32, borderRadius: 100, borderWidth: 1, borderColor: 'rgba(0, 255, 255, 0.2)', backgroundColor: 'rgba(0, 255, 255, 0.05)' },
  title: { color: '#00ffff', fontSize: 42, fontWeight: '900', letterSpacing: 4 },
  subtitle: { color: '#e2e8f0', fontSize: 12, fontFamily: 'monospace', marginTop: 8, letterSpacing: 2 },
  description: { color: 'rgba(255, 255, 255, 0.6)', fontSize: 14, textAlign: 'center', fontFamily: 'monospace', lineHeight: 22, paddingHorizontal: 20 },
  footer: { paddingBottom: 32 },
  initButton: { backgroundColor: '#00ffff', paddingVertical: 16, borderRadius: 30, alignItems: 'center', shadowColor: '#00ffff', shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.3, shadowRadius: 10, elevation: 8 },
  initText: { color: '#000000', fontSize: 16, fontWeight: 'bold', letterSpacing: 1, fontFamily: 'monospace' }
});
