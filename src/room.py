
class Room:
    """
    Data holder class for room information
    """

    def __init__(self, name, description, users_count):
        # Let's have ID as a string for the ease of further manipulation
        self.id = "-1"
        self.name = name
        self.description = description
        self.users_count = users_count
        self.user_list = []

    def __str__(self):
        return self.name

