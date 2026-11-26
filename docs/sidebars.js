/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docs: [
    'intro',
    'broadcast',
    'event-reference',
    {
      type: 'category',
      label: 'Integrations',
      collapsed: false,
      items: ['integrations/kotlin', 'integrations/java', 'integrations/unity', 'integrations/godot'],
    },
    'detection',
    'privacy',
  ],
};
export default sidebars;
