"""Destination export regression. No walking route or synthesized player-to-goal line."""
import json
import unittest
from test_navigation import rig, plain


class DestinationTest(unittest.TestCase):
    def test_same_authoritative_destination_and_prompt_clear(self):
        lua = rig()
        lua.execute("WC.focusNav.choose(42)")
        d = json.loads(lua.globals().docs[len(lua.globals().docs)])
        self.assertEqual(dict(type="destination", v="dst1", on=True, c=99, x=42, y=25), d)
        self.assertNotIn("p", d)
        lua.execute("WC.focusNav.choose(95)")
        self.assertFalse(json.loads(lua.globals().docs[len(lua.globals().docs)])["on"])

    def test_bounded_heartbeat_and_loading_clear(self):
        lua = rig()
        lua.execute("""
            WC.focusNav.choose(42);n=#docs
            for i=1,100 do WC.collectPos() end;assert(#docs==n)
            time=5;WC.collectPos();assert(#docs==n+1)
            emit('LOADING_SCREEN_ENABLED');assert(#docs==n+2)
        """)
        self.assertFalse(json.loads(lua.globals().docs[len(lua.globals().docs)])["on"])


if __name__ == "__main__":
    unittest.main()
