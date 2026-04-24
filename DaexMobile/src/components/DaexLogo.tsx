import React, { useEffect, useRef } from 'react';
import { Animated } from 'react-native';
import Svg, { G, Path } from 'react-native-svg';

const AnimatedG = Animated.createAnimatedComponent(G);

interface Props {
  size?: number;
  ambient?: boolean;
}

// Blade data: body path + tip path + fill color
const BLADES = [
  {
    body: 'M 20 80 L 40 40 L 46 40 L 26 80 Z',
    tip:  'M 40 40 L 70 16 L 72 18 L 46 40 Z',
    fill: '#00ffff',
  },
  {
    body: 'M 34 80 L 49 50 L 55 50 L 40 80 Z',
    tip:  'M 49 50 L 74 30 L 76 32 L 55 50 Z',
    fill: 'rgba(0,255,255,0.7)',
  },
  {
    body: 'M 48 80 L 58 60 L 64 60 L 54 80 Z',
    tip:  'M 58 60 L 78 44 L 80 46 L 64 60 Z',
    fill: 'rgba(0,255,255,0.4)',
  },
  {
    body: 'M 62 80 L 67 70 L 73 70 L 68 80 Z',
    tip:  'M 67 70 L 82 58 L 84 60 L 73 70 Z',
    fill: 'rgba(0,255,255,0.2)',
  },
];

const FLEX_AMOUNT = 2.5; // px the tip shifts on the x-axis
const CYCLE_DURATION = 4000;
const STAGGER = 300;

export const DaexLogo: React.FC<Props> = ({ size = 24, ambient = false }) => {
  const scale = size / 100;

  // One animated value per blade tip
  const tipAnims = useRef(BLADES.map(() => new Animated.Value(0))).current;

  useEffect(() => {
    if (!ambient) return;

    const animations = tipAnims.map((anim, i) =>
      Animated.loop(
        Animated.sequence([
          Animated.delay(i * STAGGER),
          // Snap to flexed position
          Animated.timing(anim, {
            toValue: 1,
            duration: CYCLE_DURATION * 0.25,
            useNativeDriver: true,
          }),
          // Hold flexed
          Animated.delay(CYCLE_DURATION * 0.5),
          // Return to rest
          Animated.timing(anim, {
            toValue: 0,
            duration: CYCLE_DURATION * 0.25,
            useNativeDriver: true,
          }),
        ]),
      ),
    );

    animations.forEach(a => a.start());
    return () => animations.forEach(a => a.stop());
  }, [ambient]);

  return (
    <Svg width={size} height={size} viewBox="0 0 100 100">
      {BLADES.map((blade, i) => {
        const translateX = tipAnims[i].interpolate({
          inputRange: [0, 1],
          outputRange: [0, FLEX_AMOUNT],
        });

        return (
          <G key={i}>
            {/* Body — always static */}
            <Path d={blade.body} fill={blade.fill} />
            {/* Tip — animated when ambient */}
            {ambient ? (
              <AnimatedG style={{ transform: [{ translateX }] }}>
                <Path d={blade.tip} fill={blade.fill} />
              </AnimatedG>
            ) : (
              <Path d={blade.tip} fill={blade.fill} />
            )}
          </G>
        );
      })}
    </Svg>
  );
};
