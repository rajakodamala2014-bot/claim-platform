import { useEffect, useState } from 'react';
import { staffApi, type Person } from './api';

let cache: Person[] | null = null;

export function usePeople(): Person[] {
  const [people, setPeople] = useState<Person[]>(cache ?? []);
  useEffect(() => {
    if (cache) return;
    let live = true;
    staffApi.people().then((response) => {
      cache = response.items;
      if (live) setPeople(response.items);
    }).catch(() => undefined);
    return () => { live = false; };
  }, []);
  return people;
}

/** The name to show. Falls back to the id only when the directory has not loaded that person. */
export function personName(people: Person[], id: string | null | undefined): string {
  if (!id) return '';
  return people.find((person) => person.id === id)?.name ?? id;
}
