# Building a screen in this mod

**Status:** written before the first custom screen existed. Several exist now, and **none of them is built the way the "five pieces" section below describes.** There is no registered `MenuType` in this mod and there should not be one: every screen here is opened by sending a payload the client turns into a `Screen`, because none of them is a grid of items and a menu's whole purpose is syncing slots. Read the payload recipe first; the menu recipe is kept because a screen that really does hold items would still want it.

---

## Read this first: borrow before you build

A custom screen costs a menu, a screen, a registered menu type, a network payload, a texture, and a client entry point — six things that can each be subtly wrong, in a part of the codebase no test can reach. Vanilla's screens are free, already understood by players, and already translated.

Borrow one when it fits:

| You want | Borrow |
|---|---|
| Buy and sell at prices you control | `Merchant` — implement it and call `openTradingScreen`. **The market did this first, and outgrew it; see the last section.** |
| Show a container of items | `ChestMenu.threeRows(...)` or a `SimpleContainer` — the storehouse and warehouse do exactly this |
| One line of information | `player.sendSystemMessage(...)` — every `BuildingPostBlock` already does this |
| A yes/no from the player | Two blocks, or a shift-click on one |

Build a custom one when the thing you are showing genuinely is not a grid of items — a town's vitals, a build queue with progress bars, a map, a price with a reason beside it. That is when the cost is worth paying.

### How the screens in this mod are actually built

Not as menus. Four pieces, none of them registered:

```
common/            nothing. The simulation must not know a screen exists.
neoforge/
  net/ThingPayload.java     the record, its stream codec, and a handle() that
                            calls KingdomsScreens — never Minecraft directly
  net/KingdomsNetwork.java  registration, and bump VERSION when a shape changes
  client/KingdomsScreens.java  the one class allowed to name Minecraft
  client/ThingScreen.java   a Screen, drawn with KingdomsPanel's chrome
```

The block sends the payload with `PacketDistributor.sendToPlayer`; the payload's `handle` calls `KingdomsScreens`; `KingdomsScreens` builds the screen. **The payload handler must not name `Minecraft`** — that is what `KingdomsScreens` is for, and it is the only reason that class exists.

A screen that needs to send something *back* — so far only the market's buttons — adds a second payload registered with `playToServer` and sent with `ClientPacketDistributor.sendToServer`. Everything in it is a claim by a client and none of it is evidence: re-derive the price, the stock and the player's reach on the server.

---

## What a screen that really does hold items is made of

Five pieces, in the order you should write them. Nothing works until all five exist, which is why the first one is confusing.

**Nothing in this mod is built this way.** Slots syncing themselves is the entire benefit of a menu, and no screen here has a slot in it. Reach for this only when one genuinely does.

```
common/            nothing. The simulation must not know a screen exists.
neoforge/
  menu/TownMenu.java          the SERVER-side state, and the contract
  net/TownScreenPayload.java  what the server tells the client
  KingdomsMenus.java          registration
  client/TownScreen.java      the CLIENT-side drawing
  client/KingdomsClient.java  binds screen to menu, client entry point
```

### 1. The menu — `AbstractContainerMenu`

The menu exists **on both sides**. It is the shared object: the server owns the truth, the client owns a copy, and vanilla syncs slots between them automatically.

```java
public class TownMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final Settlement settlement;   // server only; null on the client

    // SERVER constructor
    public TownMenu(int id, Inventory playerInv, Settlement settlement,
                    ContainerLevelAccess access) { ... }

    // CLIENT constructor — the one the registration below calls
    public TownMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) { ... }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) ->
                player.distanceToSqr(pos.getCenter()) < 64.0, true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;   // no slots to shift-click between
    }
}
```

**The two constructors are the whole trick.** The server builds one with real state; the client builds one from bytes off the network. If your screen shows no items at all, the client constructor takes the buffer and reads whatever you wrote into it.

`quickMoveStack` must be overridden even with no slots, or shift-clicking crashes the game. It is the single most common way a first menu breaks.

### 2. Registration

```java
public final class KingdomsMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, KingdomsMod.MOD_ID);

    public static final Supplier<MenuType<TownMenu>> TOWN =
            MENUS.register("town", () -> IMenuTypeExtension.create(TownMenu::new));
}
```

`IMenuTypeExtension.create` is NeoForge's, not vanilla's, and is what lets the client constructor take a byte buffer. Register `MENUS` in the mod constructor alongside the block and item registers.

### 3. Opening it

Server side only, from a block's `useWithoutItem` or a command:

```java
if (player instanceof ServerPlayer serverPlayer) {
    serverPlayer.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new TownMenu(id, inv, settlement, access),
            Component.literal(settlement.name())),
        buf -> buf.writeUtf(settlement.name()));   // matches the client constructor
}
```

The lambda at the end writes exactly what the client constructor reads, in the same order. Get that wrong and the client disconnects with a decoder error, which reads as a crash and is really a mismatched pair of methods.

### 4. The screen — client only

```java
public class TownScreen extends AbstractContainerScreen<TownMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "textures/gui/town.png");

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        g.blit(RenderType::guiTextured, TEXTURE, leftPos, topPos, 0, 0,
               imageWidth, imageHeight, 256, 256);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
    }
}
```

**This class must never be referenced from common code or from anything the server loads.** A dedicated server has no rendering classes at all; touching `Screen` from shared code crashes it on startup with `NoClassDefFoundError`, and it will not show up in single-player testing.

### 5. Binding it

```java
@EventBusSubscriber(modid = KingdomsMod.MOD_ID, value = Dist.CLIENT,
                    bus = EventBusSubscriber.Bus.MOD)
public final class KingdomsClient {
    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(KingdomsMenus.TOWN.get(), TownScreen::new);
    }
}
```

`value = Dist.CLIENT` is what keeps the whole class off the server.

---

## Sending data that is not items

Slots sync themselves. Anything else — a treasury figure, a hunger level, a build queue — does not, and this is where most of the work is.

**For a handful of integers**, use `DataSlot`, which vanilla syncs for you:

```java
addDataSlot(DataSlot.create(settlement::treasury, ignored -> { }));
```

Cheap, automatic, and limited to `short` range — anything above 32,767 wraps, which a treasury can exceed.

**For anything larger**, send a payload:

```java
public record TownStatePayload(int treasury, int population, List<String> events)
        implements CustomPacketPayload {

    public static final Type<TownStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(KingdomsMod.MOD_ID, "town_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TownStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TownStatePayload::treasury,
                    ByteBufCodecs.VAR_INT, TownStatePayload::population,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        TownStatePayload::events,
                    TownStatePayload::new);
}
```

Registered on `RegisterPayloadHandlersEvent`, sent with `PacketDistributor.sendToPlayer`.

**Send it when it changes, not every tick.** A screen open on a settlement that ticks every 100 ticks needs an update every 100 ticks, not twenty times a second.

---

## The traps, in the order you will hit them

1. **`quickMoveStack` not overridden.** Shift-click, instant crash. Return `ItemStack.EMPTY` if there is nothing to move.
2. **Client and server constructors disagreeing.** The buffer is written in one place and read in another and nothing checks they match. Write them next to each other and keep them that way.
3. **A client class touched from shared code.** Works in single-player, kills a dedicated server on startup. The `neoforge:runServer` task in the playtest harness catches it in about ninety seconds.
4. **Simulation state read on the client.** `KingdomsMod.simulationFor(level)` returns null client-side. Everything the screen draws must have arrived over the network.
5. **Texture path wrong.** Silent — you get a blank or garbled panel, no error. `assets/kingdoms/textures/gui/town.png`, and the sheet must be 256×256 for the default `blit`.
6. **Holding a `Settlement` in the menu and reading it on the client.** Null. This is trap 4 wearing a different hat and is the one that will actually get you, because the field is right there.

---

## Testing a screen

Honestly: **you cannot unit-test it**, and pretending otherwise wastes time. The JUnit game never binds item components, so a test cannot even hold an `ItemStack` — see the note in [GOALS.md](GOALS.md).

What can be tested is everything behind it, and that is where the logic should live:

- `Market.offers(settlement)` — what the screen would show. Fully tested in `MarketTest`.
- Payload round-trip — encode, decode, compare. No game needed.

What needs a running client:

- That it opens at all
- That it looks right
- That shift-click does not crash

The harness in the scratchpad drives a client into a staged world; `drive_client.py` is the one to use. Test the dedicated server too, because that is where the client-class mistake shows up.

---

## For the market specifically

This section used to say what a custom trading screen *would* be for. It is built, and it is for exactly that: **why** the price is what it is.

> *8 food — **They are starving.** [ Sell 48 ]*

That single line is the whole argument. A merchant screen shows a price and has nowhere at all to put the reason, and the reason is the town's entire character — a price that moves with a shortage is only a game if the shortage is legible.

The logic was already there when the presentation caught up, which is the right way round and the reason borrowing vanilla's screen first was not wasted work. `Market.Reason` is now a component of `Market.Deal` rather than something the screen works out for itself: a price and an explanation derived separately are two things that can disagree, and the explanation is the part being sold.

Worth knowing if you touch it:

- The board is re-sent after **every** trade, and `KingdomsScreens.openMarket` folds it into the open screen instead of replacing it. A stall still showing the price that was true before the town stopped starving is worse than no stall; reopening the screen for each lot throws the player's place away eight units at a time.
- One lot per press. The alternative is a count the server has to bound anyway, and a button that means exactly one thing is a button whose price the player has already read.
- Emeralds exist only at the counter, one to the coin. Every emerald in the world came out of a treasury and every one that leaves goes into one, which is why `MarketCounter` counts the payment before touching the ledger and removes it after.
