from pathlib import Path

p = Path('acceptance/onboarding-client-26.2/src/gametest/java/com/branz/mmorpg/acceptance/OnboardingFoundationClientGameTest.java')
text = p.read_text(encoding='utf-8')

old_import = 'import net.minecraft.world.inventory.ClickType;\n'
new_import = 'import java.lang.reflect.Method;\n'
if text.count(old_import) != 1:
    raise SystemExit('click enum import guard failed')
text = text.replace(old_import, new_import, 1)

old_call = '''                    client.gameMode.handleInventoryMouseClick(
                            screen.getMenu().containerId,
                            GREATSWORD_SLOT,
                            0,
                            ClickType.PICKUP,
                            client.player);
'''
new_call = '''                    clickSlot(client, screen);
'''
if text.count(old_call) != 1:
    raise SystemExit('inventory click call guard failed')
text = text.replace(old_call, new_call, 1)

marker = '''    private static void connect(ClientGameTestContext context, String address) {
'''
helper = '''    private static void clickSlot(
            net.minecraft.client.Minecraft client, AbstractContainerScreen<?> screen) {
        Method click = null;
        for (Method method : client.gameMode.getClass().getMethods()) {
            if (method.getName().equals("handleInventoryMouseClick")
                    && method.getParameterCount() == 5) {
                click = method;
                break;
            }
        }
        if (click == null) {
            throw new IllegalStateException("inventory click method is unavailable");
        }
        Class<?> actionType = click.getParameterTypes()[3];
        if (!actionType.isEnum()) {
            throw new IllegalStateException("inventory click action type is not an enum");
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        Enum<?> pickup = Enum.valueOf((Class) actionType.asSubclass(Enum.class), "PICKUP");
        try {
            click.invoke(
                    client.gameMode,
                    screen.getMenu().containerId,
                    GREATSWORD_SLOT,
                    0,
                    pickup,
                    client.player);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("inventory click invocation failed", exception);
        }
    }

'''
if text.count(marker) != 1:
    raise SystemExit('connect helper insertion guard failed')
text = text.replace(marker, helper + marker, 1)
p.write_text(text, encoding='utf-8')
