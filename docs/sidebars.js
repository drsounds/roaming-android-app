/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docs: [
    'intro',
    'broadcast',
    'low-latency',
    'event-reference',
    {
      type: 'category',
      label: 'Integrations',
      collapsed: false,
      items: ['integrations/kotlin', 'integrations/java', 'integrations/unity', 'integrations/godot'],
    },
    {
      type: 'category',
      label: 'Gamepad mode',
      collapsed: false,
      items: ['gamepad/overview', 'gamepad/mapping'],
    },
    'detection',
    'safety',
    'privacy',
  ],
};
export default sidebars;
