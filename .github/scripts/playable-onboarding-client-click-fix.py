from pathlib import Path

p = Path('acceptance/onboarding-client-26.2/src/gametest/java/com/branz/mmorpg/acceptance/OnboardingFoundationClientGameTest.java')
text = p.read_text(encoding='utf-8')

old_import = 'import net.minecraft.world.inventory.ClickType;\n'
if text.count(old_import) != 1:
    raise SystemExit('click enum import guard failed')
text = text.replace(old_import, '', 1)

old_block = '''        context.runOnClient(
                client -> {
                    if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
                        throw new IllegalStateException("starting foundation inventory did not open");
                    }
                    if (screen.getMenu().slots.size() <= GREATSWORD_SLOT) {
                        throw new IllegalStateException("starting foundation inventory is too small");
                    }
                    client.gameMode.handleInventoryMouseClick(
                            screen.getMenu().containerId,
                            GREATSWORD_SLOT,
                            0,
                            ClickType.PICKUP,
                            client.player);
                });
'''
new_block = '''        double[] cursor =
                context.computeOnClient(
                        client -> {
                            if (!(client.gui.screen()
                                    instanceof AbstractContainerScreen<?> screen)) {
                                throw new IllegalStateException(
                                        "starting foundation inventory did not open");
                            }
                            int menuSlots = screen.getMenu().slots.size();
                            if (menuSlots <= GREATSWORD_SLOT) {
                                throw new IllegalStateException(
                                        "starting foundation inventory is too small");
                            }
                            int containerSlots = menuSlots - 36;
                            if (containerSlots <= 0 || containerSlots % 9 != 0) {
                                throw new IllegalStateException(
                                        "starting foundation inventory has unexpected slot geometry: "
                                                + menuSlots);
                            }
                            int rows = containerSlots / 9;
                            int imageWidth = 176;
                            int imageHeight = 114 + rows * 18;
                            int left = (screen.width - imageWidth) / 2;
                            int top = (screen.height - imageHeight) / 2;
                            int column = GREATSWORD_SLOT % 9;
                            int row = GREATSWORD_SLOT / 9;
                            double guiX = left + 8 + column * 18 + 8;
                            double guiY = top + 18 + row * 18 + 8;
                            double rawX =
                                    guiX
                                            * client.getWindow().getScreenWidth()
                                            / (double) screen.width;
                            double rawY =
                                    guiY
                                            * client.getWindow().getScreenHeight()
                                            / (double) screen.height;
                            return new double[] {rawX, rawY};
                        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTick();
        context.getInput().pressMouse(0);
'''
if text.count(old_block) != 1:
    raise SystemExit('inventory click block guard failed')
text = text.replace(old_block, new_block, 1)
p.write_text(text, encoding='utf-8')
